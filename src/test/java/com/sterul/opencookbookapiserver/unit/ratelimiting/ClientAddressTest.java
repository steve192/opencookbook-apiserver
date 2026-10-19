package com.sterul.opencookbookapiserver.unit.ratelimiting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.ratelimiting.ClientAddress;

class ClientAddressTest {

    @Test
    void anIpv4CallerCountsByItsAddress() {
        assertEquals("203.0.113.7", ClientAddress.of("203.0.113.7"));
    }

    @Test
    void anIpv6CallerCountsByItsSlash64() {
        assertEquals(ClientAddress.of("2001:db8:1:2::1"), ClientAddress.of("2001:db8:1:2:abcd:ef01:2345:6789"));
        assertNotEquals(ClientAddress.of("2001:db8:1:2::1"), ClientAddress.of("2001:db8:1:3::1"));
    }

    @Test
    void anIpv4MappedAddressCountsAsTheIpv4Caller() {
        assertEquals("203.0.113.7", ClientAddress.of("::ffff:203.0.113.7"));
    }
}
