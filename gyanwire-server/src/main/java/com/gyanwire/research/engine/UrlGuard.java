package com.gyanwire.research.engine;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

public final class UrlGuard {

    public static final int MAX_REDIRECTS = 3;
    public static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    public static final int TIMEOUT_MS = 8_000;

    private UrlGuard() {
    }

    public static void check(URI uri, DnsLookup dns) throws UnknownHostException {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("URL is missing a host");
        }
        String scheme = uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Only http and https URLs are allowed");
        }
        InetAddress[] addresses = dns.resolve(uri.getHost());
        if (addresses == null || addresses.length == 0) {
            throw new IllegalArgumentException("URL did not resolve");
        }
        for (InetAddress address : addresses) {
            if (isBlocked(address)) {
                throw new IllegalArgumentException("Blocked address for " + uri.getHost());
            }
        }
    }

    public static boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] raw = address.getAddress();
        if (address instanceof Inet4Address && raw.length == 4) {
            int b0 = raw[0] & 0xff;
            int b1 = raw[1] & 0xff;
            if (b0 == 0 || b0 == 127) {
                return true;
            }
            if (b0 == 169 && b1 == 254) {
                return true;
            }
            if (b0 == 100 && b1 >= 64 && b1 <= 127) {
                return true;
            }
        }
        if (address instanceof Inet6Address && raw.length == 16) {
            int first = raw[0] & 0xff;
            if ((first & 0xfe) == 0xfc) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    public interface DnsLookup {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
