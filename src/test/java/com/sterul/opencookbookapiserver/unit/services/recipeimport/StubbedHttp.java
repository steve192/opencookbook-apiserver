package com.sterul.opencookbookapiserver.unit.services.recipeimport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;

/** An http client that answers from a table instead of the network, and remembers what it was asked. */
public final class StubbedHttp {

    private static final ContentType HTML = ContentType.create("text/html", StandardCharsets.UTF_8);

    private record Answer(int status, byte[] body, ContentType type, String location) {
    }

    private final Map<String, Answer> answers = new HashMap<>();
    private final List<HttpGet> requests = new ArrayList<>();
    private final CloseableHttpClient client = mock(CloseableHttpClient.class);

    public StubbedHttp() throws IOException {
        when(client.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
                .thenAnswer(invocation -> answer(invocation.getArgument(0), invocation.getArgument(1)));
    }

    public StubbedHttp page(String url, String html) {
        answers.put(url, new Answer(200, html.getBytes(StandardCharsets.UTF_8), HTML, null));
        return this;
    }

    public StubbedHttp image(String url, byte[] bytes) {
        answers.put(url, new Answer(200, bytes, ContentType.IMAGE_JPEG, null));
        return this;
    }

    public StubbedHttp status(String url, int status) {
        answers.put(url, new Answer(status, new byte[0], HTML, null));
        return this;
    }

    public StubbedHttp redirect(String url, String location) {
        answers.put(url, new Answer(302, new byte[0], HTML, location));
        return this;
    }

    public CloseableHttpClient client() {
        return client;
    }

    public List<HttpGet> requests() {
        return requests;
    }

    private Object answer(HttpGet request, HttpClientResponseHandler<?> handler) throws Exception {
        requests.add(request);
        var answer = answers.get(request.getUri().toString());
        if (answer == null) {
            throw new IOException("Nothing stubbed for " + request.getUri());
        }
        try (var response = new BasicClassicHttpResponse(answer.status())) {
            response.setEntity(new ByteArrayEntity(answer.body(), answer.type()));
            if (answer.location() != null) {
                response.setHeader(HttpHeaders.LOCATION, answer.location());
            }
            return handler.handleResponse(response);
        }
    }
}
