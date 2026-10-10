package com.gyanwire.ideas;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PatternMatcher {

    private final List<Pattern> patterns = new ArrayList<>();

    public PatternMatcher() {
        try {
            Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:ideas/patterns/*.yaml");
            Yaml yaml = new Yaml();
            for (Resource file : files) {
                try (InputStream in = file.getInputStream()) {
                    Map<String, Object> row = yaml.load(in);
                    patterns.add(new Pattern(
                            String.valueOf(row.get("id")),
                            String.valueOf(row.get("name")),
                            stringList(row.get("eventTypes")),
                            String.valueOf(row.get("ideaShape"))
                    ));
                }
            }
        } catch (Exception ignored) {
            // An empty library falls back to a generic pattern inside the idea service.
        }
    }

    public List<Pattern> match(String eventType) {
        List<Pattern> hits = new ArrayList<>();
        for (Pattern pattern : patterns) {
            if (pattern.eventTypes().contains(eventType)) {
                hits.add(pattern);
            }
        }
        if (hits.isEmpty() && !patterns.isEmpty()) {
            hits.add(patterns.get(0));
        }
        return hits.stream().limit(3).toList();
    }

    public List<Pattern> all() {
        return List.copyOf(patterns);
    }

    public List<String> shapesFor(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> shapes = new ArrayList<>();
        for (Pattern pattern : patterns) {
            if (ids.contains(pattern.id())) {
                shapes.add(pattern.ideaShape());
            }
        }
        return shapes;
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    public record Pattern(String id, String name, List<String> eventTypes, String ideaShape) {
    }
}
