package com.gyanwire.research.engine;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HttpFetchClient {

    private final UrlGuard.DnsLookup dns;
    private final FetchTransport transport;
    private final int minIntervalMs;
    private final ConcurrentHashMap<String, Object> hostLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastRequestAt = new ConcurrentHashMap<>();

    public HttpFetchClient() {
        this(InetDns.INSTANCE, new JdkFetchTransport(), 1_000);
    }

    HttpFetchClient(UrlGuard.DnsLookup dns, FetchTransport transport, int minIntervalMs) {
        this.dns = dns;
        this.transport = transport;
        this.minIntervalMs = minIntervalMs;
    }

    public String get(String url, String userAgent, int timeoutMs) throws Exception {
        int timeout = Math.min(Math.max(timeoutMs, 1), UrlGuard.TIMEOUT_MS);
        URI current = URI.create(url);
        for (int hop = 0; hop <= UrlGuard.MAX_REDIRECTS; hop++) {
            UrlGuard.check(current, dns);
            awaitHost(current.getHost());
            FetchResult result = transport.exchange(current, userAgent, timeout);
            int status = result.status();
            if (status >= 300 && status < 400) {
                if (hop == UrlGuard.MAX_REDIRECTS || result.location() == null || result.location().isBlank()) {
                    throw new IllegalStateException("Too many redirects");
                }
                current = current.resolve(result.location());
                continue;
            }
            if (status < 200 || status >= 400) {
                throw new IllegalStateException("HTTP " + status);
            }
            byte[] body = result.body() == null ? new byte[0] : result.body();
            if (body.length > UrlGuard.MAX_BODY_BYTES) {
                throw new IllegalStateException("Response body exceeds 2 MB");
            }
            return new String(body, StandardCharsets.UTF_8);
        }
        throw new IllegalStateException("Too many redirects");
    }

    private void awaitHost(String host) throws InterruptedException {
        if (minIntervalMs <= 0 || host == null || host.isBlank()) {
            return;
        }
        String key = host.toLowerCase();
        Object lock = hostLocks.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            long now = System.currentTimeMillis();
            long wait = lastRequestAt.getOrDefault(key, 0L) + minIntervalMs - now;
            if (wait > 0) {
                Thread.sleep(wait);
            }
            lastRequestAt.put(key, System.currentTimeMillis());
        }
    }

    interface FetchTransport {
        FetchResult exchange(URI uri, String userAgent, int timeoutMs) throws Exception;
    }

    record FetchResult(int status, String location, byte[] body) {
    }

    enum InetDns implements UrlGuard.DnsLookup {
        INSTANCE;

        @Override
        public java.net.InetAddress[] resolve(String host) throws java.net.UnknownHostException {
            return java.net.InetAddress.getAllByName(host);
        }
    }

    static final class JdkFetchTransport implements FetchTransport {
        private final HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(8))
                .build();

        @Override
        public FetchResult exchange(URI uri, String userAgent, int timeoutMs) throws Exception {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();
            HttpResponse<java.io.InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            byte[] body;
            try (java.io.InputStream in = res.body()) {
                body = readLimited(in);
            }
            return new FetchResult(res.statusCode(), res.headers().firstValue("location").orElse(null), body);
        }

        private static byte[] readLimited(java.io.InputStream in) throws java.io.IOException {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buf)) >= 0) {
                total += n;
                if (total > UrlGuard.MAX_BODY_BYTES) {
                    throw new IllegalStateException("Response body exceeds 2 MB");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
}
