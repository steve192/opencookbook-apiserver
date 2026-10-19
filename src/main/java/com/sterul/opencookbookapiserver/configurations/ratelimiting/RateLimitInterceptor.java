package com.sterul.opencookbookapiserver.configurations.ratelimiting;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration.CallerBudget;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.ratelimiting.ClientAddress;
import com.sterul.opencookbookapiserver.ratelimiting.RateLimitDecision;
import com.sterul.opencookbookapiserver.ratelimiting.RateLimited;
import com.sterul.opencookbookapiserver.ratelimiting.RequestBudget;
import com.sterul.opencookbookapiserver.services.SignedInUserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Spends the budget of the tier an endpoint declares with {@link RateLimited}, and refuses the request once it is
 * used up. One budget per tier, shared by all its endpoints.
 */
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Duration HOUR = Duration.ofHours(1);

    private final SignedInUserService signedInUser;
    private final CallerBudgets cheap;
    private final CallerBudgets guessable;
    private final CallerBudgets expensive;
    private final RequestBudget perShareLink;

    public RateLimitInterceptor(OpencookbookConfiguration configuration, SignedInUserService signedInUser, Clock clock) {
        var limits = configuration.getRateLimits();
        this.signedInUser = signedInUser;
        this.cheap = CallerBudgets.of(limits.getCheap(), clock);
        this.guessable = CallerBudgets.of(limits.getGuessable(), clock);
        this.expensive = CallerBudgets.of(limits.getExpensive(), clock);
        this.perShareLink = new RequestBudget(limits.getShareLinkPerDay(), Duration.ofDays(1), clock);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var limit = handler instanceof HandlerMethod method ? declaredLimit(method) : null;
        if (limit == null) {
            return true;
        }
        var client = ClientAddress.of(request.getRemoteAddr());
        var decision = spend(limit, request, client);
        if (decision.allowed()) {
            return true;
        }

        // Never log the path: it may hold the capability being spent, such as an invite token.
        log.info("Refusing a {} request from {}: rate limit exceeded", limit.value(), client);
        // Set before throwing, on the response the error body is written to: an interceptor runs inside
        // the dispatcher servlet, so the one handler that formats errors writes the body.
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, decision.retryAfter().toSeconds())));
        throw new ApiException(ApiErrorCode.RATE_LIMITED, "Rate limit exceeded");
    }

    private RateLimitDecision spend(RateLimited limit, HttpServletRequest request, String client) {
        return switch (limit.value()) {
            case CHEAP -> cheap.spend(signedInUser.signedInName(), client);
            case GUESSABLE -> guessable.spend(signedInUser.signedInName(), client);
            case EXPENSIVE -> expensive.spend(signedInUser.signedInName(), client);
            case SHARE_LINK -> perShareLink.spend(pathVariable(request, limit.perPathVariable()));
        };
    }

    private static RateLimited declaredLimit(HandlerMethod method) {
        var onMethod = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RateLimited.class);
        return onMethod != null
                ? onMethod
                : AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RateLimited.class);
    }

    private static String pathVariable(HttpServletRequest request, String name) {
        @SuppressWarnings("unchecked")
        var variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        var value = variables == null ? null : variables.get(name);
        if (value == null) {
            throw new IllegalStateException("SHARE_LINK needs the link's path variable; '" + name + "' is none");
        }
        return value;
    }

    /** A tier's budgets: per address for callers without an account, per account for signed-in users. */
    private record CallerBudgets(RequestBudget anonymous, RequestBudget signedIn) {

        static CallerBudgets of(CallerBudget budget, Clock clock) {
            return new CallerBudgets(new RequestBudget(budget.getAnonymousPerHour(), HOUR, clock),
                    new RequestBudget(budget.getSignedInPerHour(), HOUR, clock));
        }

        RateLimitDecision spend(Optional<String> account, String address) {
            return account.map(signedIn::spend).orElseGet(() -> anonymous.spend(address));
        }
    }
}
