package com.gyanwire.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class GoogleAuthClient {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper mapper;

    public GoogleAuthClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record GoogleProfile(String email, String givenName, String familyName, String picture) {}

    public GoogleProfile fetchVerifiedProfile(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) return null;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.googleapis.com/oauth2/v3/userinfo"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + accessToken.trim())
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) return null;
            JsonNode body = mapper.readTree(res.body());
            String email = text(body, "email").toLowerCase();
            if (email.isBlank()) return null;
            boolean verified = body.path("email_verified").asBoolean(false)
                    || "true".equalsIgnoreCase(body.path("email_verified").asText(""))
                    || body.path("verified_email").asBoolean(false);
            if (!verified) return null;
            String given = text(body, "given_name");
            String family = text(body, "family_name");
            String full = text(body, "name");
            if (given.isBlank() && family.isBlank() && !full.isBlank()) {
                String[] parts = full.split("\\s+", 2);
                given = parts[0];
                family = parts.length > 1 ? parts[1] : "";
            }
            return new GoogleProfile(email, given, family, text(body, "picture"));
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }
}
