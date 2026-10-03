package com.sterul.opencookbookapiserver.services.instance;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.configurations.EmailConfiguration;
import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.EmailService;
import com.sterul.opencookbookapiserver.services.FailureReason;
import com.sterul.opencookbookapiserver.services.mail.MailAvailability;
import com.sterul.opencookbookapiserver.services.ml.MlAvailabilityService;
import com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers.RecipeScraperServiceProxy;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;

/** Whether the services this instance depends on answer right now, for an administrator to look at. */
@Service
@Slf4j
public class InstanceChecks {

    /** How long a probe waits to connect and, separately, to read; a slow probe can take longer in all. */
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(4);

    /** Only a safety net for a probe that did not keep to {@link #PROBE_TIMEOUT}. */
    static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final String RECIPE_SCAN_REACHABLE_ONLY = "Reachable; the api token was not checked";

    public enum Check {
        MAIL, RECIPE_IMPORT, RECIPE_SCAN
    }

    public enum Status {
        OK, FAILED, NOT_CONFIGURED
    }

    /** @param detail why it failed, or what an OK result did not cover; null otherwise */
    public record Result(Check check, Status status, String detail) {
    }

    @FunctionalInterface
    private interface Probe {
        void run() throws IOException, MessagingException;
    }

    private final OpencookbookConfiguration configuration;
    private final MailAvailability mail;
    private final EmailService emailService;
    private final RecipeScraperServiceProxy recipeImport;
    private final Optional<MlAvailabilityService> recipeScan;
    private final TaskExecutor executor;

    public InstanceChecks(OpencookbookConfiguration configuration, MailAvailability mail,
            EmailService emailService, RecipeScraperServiceProxy recipeImport,
            Optional<MlAvailabilityService> recipeScan,
            @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.configuration = configuration;
        this.mail = mail;
        this.emailService = emailService;
        this.recipeImport = recipeImport;
        this.recipeScan = recipeScan;
        this.executor = executor;
    }

    /** All at once, none for longer than {@link #TIMEOUT}. */
    public List<Result> runAll() {
        var running = Stream.of(Check.values())
                .map(check -> CompletableFuture.supplyAsync(() -> run(check), executor)
                        .completeOnTimeout(new Result(check, Status.FAILED,
                                "No answer within " + TIMEOUT.toSeconds() + " seconds"),
                                TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                .toList();
        return running.stream().map(CompletableFuture::join).toList();
    }

    public void sendTestMail(CookpalUser administrator) {
        mail.requireSmtpHost();
        try {
            emailService.sendTestMail(administrator);
        } catch (MessagingException e) {
            throw ApiException.withReason(ApiErrorCode.MAIL_DELIVERY_FAILED, FailureReason.of(e), e);
        }
    }

    private Result run(Check check) {
        return switch (check) {
            case MAIL -> mail.hasSmtpHost()
                    ? attempt(check, this::probeMail)
                    : notConfigured(check);
            case RECIPE_IMPORT -> isBlank(configuration.getRecipeScaperServiceUrl())
                    ? notConfigured(check)
                    : attempt(check, this::probeRecipeImport);
            case RECIPE_SCAN -> recipeScan
                    .map(this::checkRecipeScan)
                    .orElseGet(() -> notConfigured(check));
        };
    }

    private void probeMail() throws MessagingException {
        EmailConfiguration.createMailSender(configuration, PROBE_TIMEOUT).testConnection();
    }

    private Result checkRecipeScan(MlAvailabilityService service) {
        var result = attempt(Check.RECIPE_SCAN, () -> service.check(PROBE_TIMEOUT));
        return result.status() == Status.OK
                ? new Result(Check.RECIPE_SCAN, Status.OK, RECIPE_SCAN_REACHABLE_ONLY)
                : result;
    }

    private void probeRecipeImport() throws IOException {
        var status = recipeImport.probe(PROBE_TIMEOUT);
        if (status < 200 || status >= 300) {
            throw new IOException("Answered with http status " + status);
        }
    }

    private static Result attempt(Check check, Probe probe) {
        try {
            probe.run();
            return new Result(check, Status.OK, null);
        } catch (Exception e) {
            var reason = FailureReason.of(e);
            log.warn("Instance check {} failed: {}", check, reason);
            return new Result(check, Status.FAILED, reason);
        }
    }

    private static Result notConfigured(Check check) {
        return new Result(check, Status.NOT_CONFIGURED, null);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
