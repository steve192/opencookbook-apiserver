package com.sterul.opencookbookapiserver.services.ml;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PreDestroy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.configurations.ml.ConditionalOnMlConfigured;
import com.sterul.opencookbookapiserver.entities.ml.MlJobStatus;

import lombok.extern.slf4j.Slf4j;

/** The only place that speaks to the machine learning subsystem. */
@Component
@ConditionalOnMlConfigured
@Slf4j
public class MlSubsystemProxy {

    private static final String JOBS_PATH = "/api/v1/jobs";
    private static final String HEALTH_PATH = "/api/v1/health";
    private static final String PAGE_EDGES_PATH = "/api/v1/page-edges";
    private static final String TRAINING_DATA_PATH = "/api/v1/training-data";
    private static final String ATTACHMENTS_FIELD = "attachments";

    private final OpencookbookConfiguration configuration;
    private final Gson gson = new Gson();

    /**
     * One client for the life of the component: it owns a connection pool, so building one per
     * call throws away every connection after a single use.
     */
    private final CloseableHttpClient httpClient = HttpClients.createDefault();

    private record RawResponse(int statusCode, String body) {
    }

    private static final HttpClientResponseHandler<RawResponse> RESPONSE_HANDLER =
            response -> new RawResponse(
                    response.getCode(),
                    response.getEntity() == null
                            ? ""
                            : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));

    public MlSubsystemProxy(OpencookbookConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * What the subsystem currently says about one job.
     *
     * @param resultJson the subsystem's result document, exactly as it arrived, or null
     */
    public record MlJobState(String jobId, MlJobStatus status, String resultJson,
            String errorCode, String errorMessage, boolean errorRetryable,
            Integer queuePosition) {
    }

    /** The page the subsystem found, as fractions of the photograph. */
    public record DetectedPage(List<double[]> corners, double confidence, boolean detected) {
    }

    /** Hand over a photographed recipe. */
    public String submitRecipeOcr(String jobType, RecipeOcrPayload payload,
            List<MultipartFile> images, boolean trainingConsent, String submitter)
            throws MlSubsystemException {

        var body = MultipartEntityBuilder.create()
                .addTextBody("job_type", jobType)
                .addTextBody("payload", gson.toJson(payload),
                        ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8))
                .addTextBody("training_consent", Boolean.toString(trainingConsent))
                .addTextBody("submitter", submitter);

        for (var image : images) {
            body.addBinaryBody(ATTACHMENTS_FIELD, readFully(image),
                    contentTypeOf(image), filenameOf(image));
        }

        var request = new HttpPost(url(JOBS_PATH));
        request.setEntity(body.build());

        var response = execute(request);
        return stringOrEmpty(parse(response.body()), "job_id");
    }

    /**
     * Where the page is in a photograph, so the app can offer a crop rather than ask for one.
     *
     * @param image the photograph
     * @return the four corners as fractions of the picture, and how sure the subsystem is
     * @throws MlSubsystemException when the subsystem refuses or cannot be reached
     */
    public DetectedPage detectPageEdges(MultipartFile image) throws MlSubsystemException {
        var body = MultipartEntityBuilder.create()
                .addBinaryBody("image", readFully(image), contentTypeOf(image), filenameOf(image))
                .build();

        var request = new HttpPost(url(PAGE_EDGES_PATH));
        request.setEntity(body);

        var parsed = parse(execute(request).body());
        try {
            return new DetectedPage(
                    parsed.getAsJsonArray("corners").asList().stream()
                            .map(corner -> new double[] {
                                    corner.getAsJsonArray().get(0).getAsDouble(),
                                    corner.getAsJsonArray().get(1).getAsDouble(),
                            })
                            .toList(),
                    has(parsed, "confidence") ? parsed.get("confidence").getAsDouble() : 0,
                    flag(parsed, "detected"));
        } catch (RuntimeException e) {
            // Gson throws whatever it likes at an unexpected shape; the caller wants to hear
            // that the subsystem answered oddly.
            throw new MlSubsystemException("ML_MALFORMED_RESPONSE",
                    "The machine learning subsystem answered with something unreadable",
                    false, e);
        }
    }

    public MlJobState fetch(String remoteJobId) throws MlSubsystemException {
        var response = execute(new HttpGet(url(JOBS_PATH + "/" + remoteJobId)));
        return toState(parse(response.body()));
    }

    /**
     * Re-read a finished scan against areas somebody marked.
     *
     * @param remoteJobId the scan to correct
     * @param corrections what was marked, in the shape the subsystem documents
     * @return the corrected job
     * @throws MlSubsystemException when the subsystem refuses or cannot be reached
     */
    public MlJobState refine(String remoteJobId, Map<String, Object> corrections)
            throws MlSubsystemException {
        var request = new HttpPost(url(JOBS_PATH + "/" + remoteJobId + "/refine"));
        request.setEntity(new StringEntity(gson.toJson(corrections), ContentType.APPLICATION_JSON));

        var response = execute(request);
        return toState(parse(response.body()));
    }

    public void cancel(String remoteJobId) throws MlSubsystemException {
        execute(new HttpDelete(url(JOBS_PATH + "/" + remoteJobId)));
    }

    /** Delete everything one person donated for training. */
    public int deleteTrainingData(String submitter) throws MlSubsystemException {
        var response = execute(new HttpDelete(url(TRAINING_DATA_PATH + "?submitter="
                + URLEncoder.encode(submitter, StandardCharsets.UTF_8))));
        var body = parse(response.body());
        return body.has("deleted") ? body.get("deleted").getAsInt() : 0;
    }

    /**
     * Ask whether the subsystem is up. The endpoint also reports queue depth and the job types
     * it knows; nothing here needs either, so this only cares that the answer arrived at all.
     *
     * @throws MlSubsystemException when the subsystem cannot be reached or refuses
     */
    public void health() throws MlSubsystemException {
        execute(new HttpGet(url(HEALTH_PATH)), false);
    }

    // -- transport -----------------------------------------------------------

    private RawResponse execute(HttpUriRequestBase request) throws MlSubsystemException {
        return execute(request, true);
    }

    private RawResponse execute(HttpUriRequestBase request, boolean authenticated)
            throws MlSubsystemException {

        var ml = configuration.getMl();
        if (authenticated) {
            request.addHeader("Authorization", "Bearer " + ml.getApiToken());
        }
        request.setConfig(RequestConfig.custom()
                .setConnectTimeout(Timeout.of(ml.getConnectTimeoutSeconds(), TimeUnit.SECONDS))
                .setResponseTimeout(Timeout.of(ml.getRequestTimeoutSeconds(), TimeUnit.SECONDS))
                .build());

        RawResponse response;
        try {
            response = httpClient.execute(request, RESPONSE_HANDLER);
        } catch (IOException e) {
            throw new MlUnavailableException("ML_UNREACHABLE",
                    "The machine learning subsystem could not be reached", e);
        }

        if (response.statusCode() >= HttpStatus.SC_OK && response.statusCode() < 300) {
            return response;
        }
        throw failureFor(response);
    }

    private MlSubsystemException failureFor(RawResponse response) {
        var error = errorOf(response.body());
        var code = error == null ? "ML_ERROR" : stringOrEmpty(error, "code");
        var message = error == null
                ? "The machine learning subsystem returned " + response.statusCode()
                : stringOrEmpty(error, "message");

        // A credential problem is about the instance, not about this request, so it reads as
        // "unavailable" and lets the feature switch itself off rather than failing per user.
        if (response.statusCode() == HttpStatus.SC_UNAUTHORIZED
                || response.statusCode() == HttpStatus.SC_FORBIDDEN) {
            log.error("The machine learning subsystem rejected our token: {}", message);
            return new MlUnavailableException(code, message);
        }
        if (response.statusCode() == HttpStatus.SC_TOO_MANY_REQUESTS) {
            return new MlQuotaExceededException(code, message);
        }
        if (response.statusCode() >= 500) {
            return new MlUnavailableException(code, message);
        }
        return new MlSubsystemException(code, message, flag(error, "retryable"));
    }

    private MlJobState toState(JsonObject body) {
        var error = has(body, "error") ? body.getAsJsonObject("error") : null;
        var result = has(body, "result") ? gson.toJson(body.get("result")) : null;

        return new MlJobState(
                stringOrEmpty(body, "job_id"),
                MlJobStatus.fromSubsystem(stringOrEmpty(body, "status")),
                result,
                error == null ? null : stringOrEmpty(error, "code"),
                error == null ? null : stringOrEmpty(error, "message"),
                flag(error, "retryable"),
                // Only present while the job is still waiting; it means nothing once it runs.
                integerOrNull(body, "queue_position"));
    }

    private JsonObject errorOf(String body) {
        try {
            var parsed = JsonParser.parseString(body);
            if (parsed.isJsonObject() && parsed.getAsJsonObject().has("error")) {
                return parsed.getAsJsonObject().getAsJsonObject("error");
            }
        } catch (JsonSyntaxException | IllegalStateException e) {
            log.debug("The subsystem's error body was not the shape we expect", e);
        }
        return null;
    }

    private JsonObject parse(String body) throws MlSubsystemException {
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new MlSubsystemException("ML_MALFORMED_RESPONSE",
                    "The machine learning subsystem answered with something unreadable", false, e);
        }
    }

    /** Whether a field is there and carries a value. Missing and null both happen. */
    private static boolean has(JsonObject body, String field) {
        return body != null && body.has(field) && !body.get(field).isJsonNull();
    }

    private static String stringOrEmpty(JsonObject body, String field) {
        return has(body, field) ? body.get(field).getAsString() : "";
    }

    private static Integer integerOrNull(JsonObject body, String field) {
        return has(body, field) ? body.get(field).getAsInt() : null;
    }

    private static boolean flag(JsonObject body, String field) {
        return has(body, field) && body.get(field).getAsBoolean();
    }

    private static byte[] readFully(MultipartFile image) throws MlSubsystemException {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new MlSubsystemException("ML_UPLOAD_UNREADABLE",
                    "An uploaded image could not be read", false, e);
        }
    }

    private static ContentType contentTypeOf(MultipartFile image) {
        var declared = image.getContentType();
        try {
            return declared == null ? ContentType.IMAGE_JPEG : ContentType.parse(declared);
        } catch (RuntimeException e) {
            return ContentType.IMAGE_JPEG;
        }
    }

    private static String filenameOf(MultipartFile image) {
        var name = image.getOriginalFilename();
        return name == null || name.isBlank() ? "page.jpg" : name;
    }

    private String url(String path) {
        return configuration.getMl().getServiceUrl().replaceAll("/+$", "") + path;
    }

    @PreDestroy
    void closeHttpClient() {
        try {
            httpClient.close();
        } catch (IOException e) {
            log.debug("The http client did not close cleanly", e);
        }
    }
}
