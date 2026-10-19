package com.sterul.opencookbookapiserver.services.mail;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.ratelimiting.RequestBudget;

/**
 * How often anyone's inbox may be written to on somebody else's request, so this server cannot be used to post
 * mail to people who never asked for it. Counted per recipient: a caller with many addresses would slip a
 * per-caller budget.
 */
@Component
public class MailAllowance {

    private final RequestBudget mailsPerRecipient;

    public MailAllowance(OpencookbookConfiguration configuration, Clock clock) {
        this.mailsPerRecipient = new RequestBudget(configuration.getAuth().getMailsPerHourPerAddress(), Duration.ofHours(1),
                clock);
    }

    /**
     * Whether one more mail may be sent to an address. Answers rather than refuses: the callers
     * reply the same whether or not an account exists, and a refusal would give that away.
     *
     * @param emailAddress who would be written to
     * @return whether to send
     */
    public boolean mayMail(String emailAddress) {
        return mailsPerRecipient.spend(emailAddress.toLowerCase(Locale.ROOT)).allowed();
    }
}
