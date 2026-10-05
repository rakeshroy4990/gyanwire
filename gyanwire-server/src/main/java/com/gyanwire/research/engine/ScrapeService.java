package com.gyanwire.research.engine;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

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

    private final HttpFetchClient httpFetchClient;

    public ScrapeService(HttpFetchClient httpFetchClient) {
        this.httpFetchClient = httpFetchClient;
    }

    public Map<String, Map<String, Object>> scrapeMany(List<String> urls, int concurrency, int timeoutMs) {
        Map<String, Map<String, Object>> out = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, concurrency));
        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (String url : urls) {
                futures.add(pool.submit(() -> {
                    try {
                        String html = httpFetchClient.get(url, "GyanwireBot/1.0 (+local research tool)", timeoutMs);
                        Document doc = Jsoup.parse(html, url);
                        String title = doc.title();
                        String description = "";
                        if (doc.selectFirst("meta[name=description]") != null) {
                            description = doc.selectFirst("meta[name=description]").attr("content");
                        }
                        String text = doc.body() == null ? "" : doc.body().text();
                        String snippet = text.length() > 320 ? text.substring(0, 320) : text;
                        Map<String, Object> page = new HashMap<>();
                        page.put("title", title);
                        page.put("description", description);
                        page.put("snippet", snippet);
                        page.put("text", text.length() > 8000 ? text.substring(0, 8000) : text);
                        page.put("ok", true);
                        out.put(url, page);
                    } catch (Exception e) {
                        out.put(url, Map.of("ok", false));
                    }
                }));
            }
            for (Future<?> f : futures) {
                try { f.get(timeoutMs + 1000L, TimeUnit.MILLISECONDS); } catch (Exception ignored) {}
            }
        } finally {
            pool.shutdownNow();
        }
        return out;
    }
}
