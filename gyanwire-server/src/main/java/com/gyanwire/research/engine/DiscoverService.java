package com.gyanwire.research.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.research.ResearchException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DiscoverService {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final String BOT_UA = "GyanwireBot/1.0 (+local research tool)";

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final ObjectMapper mapper;
    private final String searxngUrl;

    public DiscoverService(ObjectMapper mapper, @Value("${app.searxng.url:}") String searxngUrl) {
        this.mapper = mapper;
        this.searxngUrl = searxngUrl == null ? "" : searxngUrl.trim().replaceAll("/$", "");
    }

    public List<Map<String, String>> discoverPages(String query, int limit, List<String> categories) {
        Map<String, Map<String, String>> seen = new LinkedHashMap<>();
        for (String variant : IndiaSupport.indiaFirstQueries(query)) {
            if (seen.size() >= limit) break;
            try { merge(seen, searchDuckDuckGo(variant, limit), limit); } catch (Exception ignored) {}
            try { merge(seen, searchWikipedia(shortQuery(variant), Math.max(3, (limit + 2) / 3)), limit); } catch (Exception ignored) {}
            try { merge(seen, searchSearxng(variant, limit), limit); } catch (Exception ignored) {}
        }
        if (seen.size() < Math.min(4, limit)) {
            merge(seen, categorySeeds(categories, query), limit);
        }
        if (seen.isEmpty()) {
            throw new ResearchException(
                    "Could not discover pages right now. Check your network and try again.",
                    "DISCOVERY_EMPTY", 502);
        }
        List<Map<String, String>> results = new ArrayList<>(seen.values());
        results = IndiaSupport.sortIndiaFirst(results, item -> host(item.get("url")));
        return results.subList(0, Math.min(limit, results.size()));
    }

    private List<Map<String, String>> searchDuckDuckGo(String query, int limit) throws Exception {
        String body = "q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&b=&kl=in-en";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://html.duckduckgo.com/html/"))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", BROWSER_UA)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept-Language", "en-IN,en;q=0.9")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) throw new IllegalStateException("ddg " + res.statusCode());
        String html = res.body();
        if (html.contains("anomaly") && !html.contains("result__a")) {
            throw new IllegalStateException("ddg challenge");
        }
        Document doc = Jsoup.parse(html);
        List<Map<String, String>> out = new ArrayList<>();
        for (Element el : doc.select(".result")) {
            if (out.size() >= limit) break;
            Element a = el.selectFirst("a.result__a");
            if (a == null) continue;
            String resolved = resolveDdgUrl(a.attr("href"));
            if (resolved == null) continue;
            Element snip = el.selectFirst(".result__snippet");
            out.add(Map.of(
                    "title", a.text(),
                    "url", resolved,
                    "description", snip == null ? "" : snip.text(),
                    "source", "duckduckgo"
            ));
        }
        return out;
    }

    private List<Map<String, String>> searchWikipedia(String query, int limit) throws Exception {
        if (query == null || query.isBlank()) return List.of();
        String endpoint = "https://en.wikipedia.org/w/api.php?action=opensearch&search="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&limit=" + limit + "&namespace=0&format=json";
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10)).header("User-Agent", BOT_UA).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) throw new IllegalStateException("wiki " + res.statusCode());
        JsonNode data = mapper.readTree(res.body());
        List<Map<String, String>> out = new ArrayList<>();
        JsonNode titles = data.path(1);
        JsonNode descriptions = data.path(2);
        JsonNode urls = data.path(3);
        for (int i = 0; i < titles.size(); i++) {
            String url = urls.path(i).asText("");
            if (url.isBlank()) continue;
            out.add(Map.of(
                    "title", titles.path(i).asText(""),
                    "url", url,
                    "description", descriptions.path(i).asText(""),
                    "source", "wikipedia"
            ));
        }
        return out;
    }

    private List<Map<String, String>> searchSearxng(String query, int limit) throws Exception {
        if (searxngUrl.isBlank() || searxngUrl.contains("your-")) return List.of();
        String url = searxngUrl + "/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&format=json&categories=general&language=en";
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                .timeout(Duration.ofSeconds(12)).header("User-Agent", BOT_UA).header("Accept", "application/json").GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) throw new IllegalStateException("searx " + res.statusCode());
        JsonNode data = mapper.readTree(res.body());
        List<Map<String, String>> out = new ArrayList<>();
        for (JsonNode item : data.path("results")) {
            if (out.size() >= limit) break;
            String itemUrl = item.path("url").asText("");
            if (itemUrl.isBlank()) continue;
            out.add(Map.of(
                    "title", item.path("title").asText(""),
                    "url", itemUrl,
                    "description", item.path("content").asText(item.path("description").asText("")),
                    "source", "searxng"
            ));
        }
        return out;
    }

    private static List<Map<String, String>> categorySeeds(List<String> categories, String query) {
        Map<String, List<Map<String, String>>> seeds = Map.of(
                "IT", List.of(
                        Map.of("title", "arXiv cs", "url", "https://arxiv.org/list/cs/recent", "description", "Computer science research preprints", "source", "category-seed"),
                        Map.of("title", "IEEE Xplore", "url", "https://ieeexplore.ieee.org/", "description", "Engineering and computing research", "source", "category-seed")
                ),
                "Medical", List.of(
                        Map.of("title", "PubMed", "url", "https://pubmed.ncbi.nlm.nih.gov/", "description", "Biomedical research literature", "source", "category-seed"),
                        Map.of("title", "ClinicalTrials.gov", "url", "https://clinicaltrials.gov/", "description", "Registered clinical studies", "source", "category-seed")
                ),
                "Space", List.of(
                        Map.of("title", "ISRO", "url", "https://www.isro.gov.in/", "description", "Indian space research organisation", "source", "category-seed"),
                        Map.of("title", "NASA", "url", "https://www.nasa.gov/", "description", "Space research and missions", "source", "category-seed")
                ),
                "Share Market", List.of(
                        Map.of("title", "Moneycontrol Markets", "url", "https://www.moneycontrol.com/stocksmarketsindia/", "description", "Indian share market news", "source", "category-seed"),
                        Map.of("title", "NSE India", "url", "https://www.nseindia.com/", "description", "National Stock Exchange of India", "source", "category-seed")
                )
        );
        List<Map<String, String>> pages = new ArrayList<>();
        if (categories == null) return pages;
        for (String category : categories) {
            pages.addAll(seeds.getOrDefault(category, List.of()));
        }
        return pages;
    }

    private static void merge(Map<String, Map<String, String>> seen, List<Map<String, String>> batch, int limit) {
        for (Map<String, String> item : batch) {
            if (seen.size() >= limit) return;
            String url = item.get("url");
            if (url == null || url.isBlank() || seen.containsKey(url)) continue;
            if (!url.startsWith("http")) continue;
            seen.put(url, item);
        }
    }

    private static String resolveDdgUrl(String href) {
        if (href == null || href.isBlank()) return null;
        try {
            if (href.startsWith("//")) href = "https:" + href;
            if (href.contains("uddg=")) {
                String q = URI.create(href).getQuery();
                if (q != null) {
                    for (String part : q.split("&")) {
                        if (part.startsWith("uddg=")) {
                            return URLDecoder.decode(part.substring(5), StandardCharsets.UTF_8);
                        }
                    }
                }
            }
            if (href.startsWith("http")) return href;
        } catch (Exception ignored) {}
        return null;
    }

    private static String shortQuery(String query) {
        return String.valueOf(query).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s'-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String host(String url) {
        try { return URI.create(url).getHost(); } catch (Exception e) { return ""; }
    }
}
