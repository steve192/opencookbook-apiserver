package com.sterul.opencookbookapiserver.services;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.ratelimiting.EvictableRateLimits;
import com.sterul.opencookbookapiserver.ratelimiting.FixedWindowRateLimiter;
import com.sterul.opencookbookapiserver.ratelimiting.RateLimitDecision;

/**
 * What somebody who is not signed in may do, and how often anyone's inbox may be written to.
 * Counted differently because they answer different threats: guessing passwords, and using this
 * server to post mail to somebody who never asked for it.
 */
@Component
public class AuthRateLimiter implements EvictableRateLimits {

    private static final Duration WINDOW = Duration.ofHours(1);

    private static final int MAX_TRACKED_CALLERS = 10_000;

    private final FixedWindowRateLimiter attemptsPerAddress;
    private final FixedWindowRateLimiter mailsPerRecipient;

    public AuthRateLimiter(OpencookbookConfiguration configuration, Clock clock) {
        var auth = configuration.getAuth();
        this.attemptsPerAddress = new FixedWindowRateLimiter(
                auth.getAttemptsPerHourPerIp(), WINDOW, MAX_TRACKED_CALLERS, clock);
        this.mailsPerRecipient = new FixedWindowRateLimiter(
                auth.getMailsPerHourPerAddress(), WINDOW, MAX_TRACKED_CALLERS, clock);
    }

    public RateLimitDecision recordAttempt(String clientAddress) {
        return attemptsPerAddress.tryAcquire(clientAddress);
    }

    /**
     * Whether one more mail may be sent to an address. Answers rather than refuses: the callers
     * reply the same whether or not an account exists, and a refusal would give that away.
     *
     * @param emailAddress who would be written to
     * @return whether to send
     */
    public boolean mayMail(String emailAddress) {
        return mailsPerRecipient.tryAcquire(emailAddress.toLowerCase(Locale.ROOT)).allowed();
    }

    @Override
    public int evictEndedWindows() {
        return attemptsPerAddress.evictEndedWindows() + mailsPerRecipient.evictEndedWindows();
    }
}
