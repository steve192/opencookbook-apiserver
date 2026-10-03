package com.sterul.opencookbookapiserver.unit.services.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.services.AppLinkFactory;
import com.sterul.opencookbookapiserver.services.mail.MailAvailability;

class MailAvailabilityTest {

    @ParameterizedTest
    @CsvSource(nullValues = "-", value = {
            "smtp.example.com, cookpal@example.com, https://cookpal.example, true",
            "-, cookpal@example.com, https://cookpal.example, false",
            "smtp.example.com, -, https://cookpal.example, false",
            "smtp.example.com, cookpal@example.com, -, false",
            "smtp.example.com, '  ', https://cookpal.example, false",
            "-, -, -, false"
    })
    void mailNeedsAHostASenderAndTheInstanceAddress(String host, String from, String url, boolean enabled) {
        var configuration = new OpencookbookConfiguration();
        configuration.setSmtpHost(host);
        configuration.setMailFrom(from);
        configuration.setInstanceURL(url);

        var cut = new MailAvailability(configuration, new AppLinkFactory(configuration));

        assertEquals(enabled, cut.isEnabled());
    }
}
