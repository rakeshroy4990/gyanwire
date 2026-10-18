package com.gyanwire.news.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Live GitHub repository search. Uses {@code GITHUB_TOKEN} when configured.
 */
@Component
public class GitHubSearchClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubSearchClient.class);

    private final GitHubProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    @Autowired
    public GitHubSearchClient(GitHubProperties properties, ObjectMapper mapper) {
        this(properties, mapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(properties.getConnectTimeoutMs(), 1000)))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    /** Test hook — not a Spring bean constructor. */
    GitHubSearchClient(GitHubProperties properties, ObjectMapper mapper, HttpClient http) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = http;
    }

    public List<GitHubRepo> search(String query, String sub) {
        String q = prepareQuery(query, sub);
        if (q.isBlank()) {
            return List.of();
        }
        String encoded = URLEncoder.encode(q, StandardCharsets.UTF_8);
        String url = properties.getApiBase().replaceAll("/$", "")
                + "/search/repositories?q=" + encoded
                + "&sort=stars&order=desc&per_page=" + properties.getPerPage();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(properties.getReadTimeoutMs(), 2000)))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "GyanwireBot/1.0")
                    .GET();
            if (properties.getToken() != null && !properties.getToken().isBlank()) {
                builder.header("Authorization", "Bearer " + properties.getToken());
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 403 || response.statusCode() == 429) {
                log.info("GitHub search rate-limited: {}", response.statusCode());
                return List.of();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.info("GitHub search failed: status={} body={}", response.statusCode(),
                        truncate(response.body(), 200));
                return List.of();
            }
            return parse(response.body());
        } catch (Exception ex) {
            log.info("GitHub search error: {}", ex.getMessage());
            return List.of();
        }
    }

    static String prepareQuery(String query, String sub) {
        String q = query == null ? "" : query.trim();
        if (q.isBlank()) {
            return "";
        }
        if ("Trending".equalsIgnoreCase(sub == null ? "" : sub.trim()) && !q.contains("pushed:")) {
            LocalDate since = LocalDate.now(ZoneOffset.UTC).minusDays(30);
            q = q + " pushed:>" + since;
        }
        return q;
    }

    List<GitHubRepo> parse(String body) throws Exception {
        JsonNode root = mapper.readTree(body == null ? "{}" : body);
        JsonNode items = root.path("items");
        if (!items.isArray()) {
            return List.of();
        }
        List<GitHubRepo> out = new ArrayList<>();
        for (JsonNode item : items) {
            GitHubRepo repo = mapItem(item);
            if (repo != null && repo.usable()) {
                out.add(repo);
            }
        }
        return out;
    }

    private static GitHubRepo mapItem(JsonNode item) {
        if (item == null || item.isMissingNode()) {
            return null;
        }
        String fullName = text(item, "full_name");
        String htmlUrl = text(item, "html_url");
        if (fullName.isBlank() || htmlUrl.isBlank()) {
            return null;
        }
        String licence = "";
        JsonNode licenceNode = item.path("license");
        if (licenceNode.isObject()) {
            licence = text(licenceNode, "spdx_id");
            if (licence.isBlank() || "NOASSERTION".equalsIgnoreCase(licence)) {
                licence = text(licenceNode, "name");
            }
        }
        return new GitHubRepo(
                fullName,
                htmlUrl,
                text(item, "description"),
                text(item, "language"),
                item.path("stargazers_count").asLong(0),
                item.path("forks_count").asLong(0),
                licence,
                parseInstant(text(item, "pushed_at")),
                item.path("archived").asBoolean(false),
                item.path("fork").asBoolean(false)
        );
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
