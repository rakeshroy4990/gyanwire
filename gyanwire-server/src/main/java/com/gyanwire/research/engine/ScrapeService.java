package com.gyanwire.research.engine;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service
public class ScrapeService {

    static final String USER_AGENT = "GyanwireBot/1.0 (+local research tool)";

    private final HttpFetchClient httpFetchClient;
    private final ConcurrentHashMap<String, RobotsTxt> robotsCache = new ConcurrentHashMap<>();

    public ScrapeService(HttpFetchClient httpFetchClient) {
        this.httpFetchClient = httpFetchClient;
    }

    public Map<String, Map<String, Object>> scrapeMany(List<String> urls, int concurrency, int timeoutMs) {
        Map<String, Map<String, Object>> out = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, concurrency));
        int timeout = Math.min(Math.max(timeoutMs, 1), UrlGuard.TIMEOUT_MS);
        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (String url : urls) {
                futures.add(pool.submit(() -> out.put(url, fetchOne(url, timeout))));
            }
            for (Future<?> f : futures) {
                try { f.get(timeout + 1000L, TimeUnit.MILLISECONDS); } catch (Exception ignored) {}
            }
        } finally {
            pool.shutdownNow();
        }
        return out;
    }

    private Map<String, Object> fetchOne(String url, int timeoutMs) {
        try {
            URI uri = URI.create(url);
            if (!robotsAllowed(uri, timeoutMs)) {
                return Map.of("ok", false, "reason", "robots");
            }
            String html = httpFetchClient.get(url, USER_AGENT, timeoutMs);
            Document doc = Jsoup.parse(html, url);
            String text = PageText.visibleText(doc);
            String description = "";
            if (doc.selectFirst("meta[name=description]") != null) {
                description = doc.selectFirst("meta[name=description]").attr("content");
            }
            String snippet = text.length() > 320 ? text.substring(0, 320) : text;
            String stored = text.length() > 8000 ? text.substring(0, 8000) : text;
            Map<String, Object> page = new HashMap<>();
            page.put("title", doc.title());
            page.put("description", description);
            page.put("snippet", snippet);
            page.put("text", stored);
            page.put("modelText", PageText.forModel(stored));
            Instant published = PublishedDates.fromDocument(doc);
            if (published != null) {
                PublishedDates.apply(page, published);
            }
            page.put("ok", true);
            return page;
        } catch (Exception e) {
            return Map.of("ok", false);
        }
    }

    private boolean robotsAllowed(URI uri, int timeoutMs) {
        String path = uri.getPath();
        if (path != null && path.equals("/robots.txt")) {
            return true;
        }
        if (uri.getHost() == null || uri.getScheme() == null) {
            return false;
        }
        String origin = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        RobotsTxt rules = robotsCache.computeIfAbsent(origin, key -> {
            try {
                return RobotsTxt.parse(httpFetchClient.get(key + "/robots.txt", USER_AGENT, timeoutMs));
            } catch (Exception e) {
                return RobotsTxt.parse("");
            }
        });
        return rules.allows(path);
    }
}
