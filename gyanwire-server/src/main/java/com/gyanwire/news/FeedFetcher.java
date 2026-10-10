package com.gyanwire.news;

import com.gyanwire.research.engine.UrlGuard;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FeedFetcher {

    public record Fetch(int status, String etag, String lastModified, List<FeedParser.Item> items, String error) {
        public boolean notModified() {
            return status == 304;
        }

        public boolean ok() {
            return error == null && status >= 200 && status < 300;
        }
    }

    private final HttpClient http;
    private final UrlGuard.DnsLookup dns;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int hostSpacingMs;
    private final String userAgent;
    private final ConcurrentHashMap<String, Long> lastHit = new ConcurrentHashMap<>();

    @Autowired
    public FeedFetcher(NewsProperties properties) {
        this(HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofMillis(properties.getIngest().getConnectTimeoutMs()))
                        .build(),
                InetLookup.INSTANCE,
                properties.getIngest().getConnectTimeoutMs(),
                properties.getIngest().getReadTimeoutMs(),
                properties.getIngest().getHostSpacingMs(),
                properties.getIngest().getUserAgent());
    }

    FeedFetcher(HttpClient http, UrlGuard.DnsLookup dns, int connectTimeoutMs, int readTimeoutMs, int hostSpacingMs, String userAgent) {
        this.http = http;
        this.dns = dns;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.hostSpacingMs = hostSpacingMs;
        this.userAgent = userAgent;
    }

    public Fetch fetch(String url, String etag, String lastModified) {
        try {
            URI uri = URI.create(url);
            UrlGuard.check(uri, dns);
            awaitHost(uri.getHost());
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(Math.max(readTimeoutMs, 1000)))
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml");
            if (etag != null && !etag.isBlank()) {
                builder.header("If-None-Match", etag);
            }
            if (lastModified != null && !lastModified.isBlank()) {
                builder.header("If-Modified-Since", lastModified);
            }
            HttpResponse<String> response = http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 304) {
                return new Fetch(304, etag, lastModified, List.of(), null);
            }
            if (response.statusCode() >= 400) {
                return new Fetch(response.statusCode(), null, null, List.of(), "HTTP " + response.statusCode());
            }
            return new Fetch(
                    response.statusCode(),
                    response.headers().firstValue("etag").orElse(null),
                    response.headers().firstValue("last-modified").orElse(null),
                    FeedParser.parse(response.body()),
                    null
            );
        } catch (Exception ex) {
            return new Fetch(0, null, null, List.of(), ex.getMessage() == null ? "fetch failed" : ex.getMessage());
        }
    }

    private void awaitHost(String host) {
        if (host == null || hostSpacingMs <= 0) {
            return;
        }
        String key = host.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        Long previous = lastHit.put(key, now);
        if (previous == null) {
            return;
        }
        long wait = hostSpacingMs - (now - previous);
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            lastHit.put(key, System.currentTimeMillis());
        }
    }

    enum InetLookup implements UrlGuard.DnsLookup {
        INSTANCE;

        @Override
        public java.net.InetAddress[] resolve(String host) throws java.net.UnknownHostException {
            return java.net.InetAddress.getAllByName(host);
        }
    }
}
