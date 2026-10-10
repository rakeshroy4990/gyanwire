package com.gyanwire.news;

import com.gyanwire.research.engine.UrlGuard;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RedirectResolver {

    public record Resolved(String url, String domain, boolean resolved) {
    }

    private final HttpClient http;
    private final UrlGuard.DnsLookup dns;
    private final int timeoutMs;
    private final String userAgent;

    @Autowired
    public RedirectResolver(NewsProperties properties) {
        this(HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .connectTimeout(Duration.ofMillis(properties.getIngest().getRedirectTimeoutMs()))
                        .build(),
                FeedFetcher.InetLookup.INSTANCE,
                properties.getIngest().getRedirectTimeoutMs(),
                properties.getIngest().getUserAgent());
    }

    RedirectResolver(HttpClient http, UrlGuard.DnsLookup dns, int timeoutMs, String userAgent) {
        this.http = http;
        this.dns = dns;
        this.timeoutMs = timeoutMs;
        this.userAgent = userAgent;
    }

    public Resolved resolve(String url) {
        if (url == null || url.isBlank() || !NewsTexts.aggregator(url)) {
            String domain = NewsTexts.domainOf(url);
            return new Resolved(url, domain, domain != null && !domain.isBlank());
        }
        URI current;
        try {
            current = URI.create(url);
        } catch (Exception ex) {
            return new Resolved(url, NewsTexts.domainOf(url), false);
        }
        try {
            for (int hop = 0; hop <= UrlGuard.MAX_REDIRECTS; hop++) {
                UrlGuard.check(current, dns);
                HttpRequest request = HttpRequest.newBuilder(current)
                        .timeout(Duration.ofMillis(Math.max(timeoutMs, 500)))
                        .header("User-Agent", userAgent)
                        .method("GET", HttpRequest.BodyPublishers.noBody())
                        .build();
                HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    String location = response.headers().firstValue("location").orElse("");
                    if (location.isBlank()) {
                        break;
                    }
                    current = current.resolve(location);
                    continue;
                }
                if (status >= 200 && status < 300 && hop > 0) {
                    String finalUrl = current.toString();
                    return new Resolved(finalUrl, NewsTexts.domainOf(finalUrl), true);
                }
                break;
            }
        } catch (Exception ignored) {
            return new Resolved(url, NewsTexts.domainOf(url), false);
        }
        return new Resolved(url, NewsTexts.domainOf(url), false);
    }
}
