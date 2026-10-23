package com.gyanwire.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

@Component
public class GoogleAuthClient {

    private final ObjectMapper mapper;
    /** Not final so tests can swap the transport without a second constructor. */
    private Function<String, String> userInfoFetcher;

    /**
     * Only constructor. Spring requires a single unambiguous ctor for {@code @Component}
     * beans — do not add another constructor for tests.
     */
    public GoogleAuthClient(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.userInfoFetcher = defaultFetcher();
    }

    /** Test-only hook. Prefer this over adding constructors Spring will see. */
    void useFetcherForTest(Function<String, String> userInfoFetcher) {
        this.userInfoFetcher = Objects.requireNonNull(userInfoFetcher, "userInfoFetcher");
    }

    private static Function<String, String> defaultFetcher() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        return accessToken -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://www.googleapis.com/oauth2/v3/userinfo"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", "Bearer " + accessToken.trim())
                        .GET()
                        .build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() < 200 || res.statusCode() >= 300) {
                    return null;
                }
                return res.body();
            } catch (Exception e) {
                return null;
            }
        };
    }

    public record GoogleProfile(String email, String givenName, String familyName, String picture) {}

    public GoogleProfile fetchVerifiedProfile(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        try {
            String body = userInfoFetcher.apply(accessToken.trim());
            if (body == null || body.isBlank()) {
                return null;
            }
            return parseUserInfo(body);
        } catch (Exception e) {
            return null;
        }
    }

    /** Parses Google userinfo JSON. Package-visible for unit tests. */
    GoogleProfile parseUserInfo(String json) throws Exception {
        JsonNode body = mapper.readTree(json);
        String email = text(body, "email").toLowerCase(Locale.ROOT);
        if (email.isBlank()) {
            return null;
        }
        if (!emailVerified(body)) {
            return null;
        }
        String given = text(body, "given_name");
        String family = text(body, "family_name");
        String full = text(body, "name");
        if (given.isBlank() && family.isBlank() && !full.isBlank()) {
            String[] parts = full.split("\\s+", 2);
            given = parts[0];
            family = parts.length > 1 ? parts[1] : "";
        }
        return new GoogleProfile(email, given, family, text(body, "picture"));
    }

    static boolean emailVerified(JsonNode body) {
        return body.path("email_verified").asBoolean(false)
                || "true".equalsIgnoreCase(body.path("email_verified").asText(""))
                || body.path("verified_email").asBoolean(false);
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }
}
