package com.gyanwire.api;

import com.gyanwire.ideas.IdeaService;
import com.gyanwire.research.ResearchException;
import com.gyanwire.research.service.SearchEngineService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class McpService {

    private final String apiKey;
    private final SearchEngineService search;
    private final IdeaService ideas;

    public McpService(
            @Value("${app.mcp.api-key:}") String apiKey,
            SearchEngineService search,
            IdeaService ideas
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.search = search;
        this.ideas = ideas;
    }

    public Map<String, Object> handle(String key, Map<String, Object> body) {
        if (apiKey.isBlank() || key == null || !apiKey.equals(key)) {
            throw new ResearchException("API key required.", "AUTH_UNAUTHORIZED", 401);
        }
        String method = String.valueOf(body.getOrDefault("method", ""));
        Object id = body.get("id");
        if ("tools/list".equals(method)) {
            return Map.of("jsonrpc", "2.0", "id", id, "result", Map.of("tools", List.of(
                    Map.of("name", "search_research"),
                    Map.of("name", "get_findings"),
                    Map.of("name", "generate_idea")
            )));
        }
        if (!"tools/call".equals(method)) {
            throw new ResearchException("Unsupported MCP method.", "VALIDATION_ERROR", 400);
        }
        Object params = body.get("params");
        String name = "";
        Map<String, Object> arguments = Map.of();
        if (params instanceof Map<?, ?> map) {
            name = String.valueOf(map.get("name"));
            arguments = argumentMap(map.get("arguments"));
        }
        if (!List.of("search_research", "get_findings", "generate_idea").contains(name)) {
            throw new ResearchException("Unknown tool.", "VALIDATION_ERROR", 400);
        }
        Object payload = switch (name) {
            case "search_research", "get_findings" -> search.findBestResults(
                    List.of(String.valueOf(arguments.getOrDefault("industry", "IT"))),
                    null,
                    String.valueOf(arguments.getOrDefault("thoughts", arguments.getOrDefault("query", ""))),
                    5
            );
            default -> idea(arguments);
        };
        return Map.of("jsonrpc", "2.0", "id", id, "result", Map.of(
                "content", List.of(Map.of("type", "text", "text", "Untrusted tool output.")),
                "structured", payload,
                "isError", false
        ));
    }

    private Map<String, Object> argumentMap(Object arguments) {
        if (arguments instanceof String text) {
            if (text.toLowerCase().contains("select ") || text.toLowerCase().contains("password")) {
                throw new ResearchException("Tool input rejected.", "VALIDATION_ERROR", 400);
            }
            return Map.of("thoughts", text);
        }
        if (!(arguments instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String value = String.valueOf(entry.getValue());
            if (value.toLowerCase().contains("select ") || value.toLowerCase().contains("password")) {
                throw new ResearchException("Tool input rejected.", "VALIDATION_ERROR", 400);
            }
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    private Object idea(Map<String, Object> arguments) {
        Object user = arguments.get("userId");
        if (user == null || String.valueOf(user).isBlank()) {
            throw new ResearchException("generate_idea needs a consented user id.", "VALIDATION_ERROR", 400);
        }
        return ideas.fromNews(UUID.fromString(String.valueOf(user)), arguments);
    }
}
