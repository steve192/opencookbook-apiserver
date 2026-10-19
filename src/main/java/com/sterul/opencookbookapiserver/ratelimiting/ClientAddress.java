package com.sterul.opencookbookapiserver.ratelimiting;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Whom a per-address limit counts. An IPv6 subscriber usually holds a whole /64 and may use any
 * address in it, so IPv6 callers count by their /64; IPv4 callers by their address.
 */
public final class ClientAddress {

    private static final int IPV6_PREFIX_BYTES = 8;

    private ClientAddress() {
    }

    public static String of(String remoteAddress) {
        if (!remoteAddress.contains(":")) {
            return remoteAddress;
        }
        try {
            // A string with a colon is parsed as a literal, never looked up.
            var address = InetAddress.getByName(remoteAddress);
            if (address instanceof Inet4Address) {
                return address.getHostAddress();
            }
            var bytes = address.getAddress();
            Arrays.fill(bytes, IPV6_PREFIX_BYTES, bytes.length, (byte) 0);
            return InetAddress.getByAddress(bytes).getHostAddress() + "/64";
        } catch (UnknownHostException notAnAddress) {
            return remoteAddress;
        }
    }
}
