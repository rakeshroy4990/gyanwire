package com.gyanwire.ideas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the same map shape {@code fromNews} used to return as one JSON body.
 */
public final class CollectingIdeaSink implements IdeaSink {

    private final Map<String, Object> meta = new LinkedHashMap<>();
    private final List<Map<String, Object>> ideas = new ArrayList<>();

    @Override
    public void status(String stage) {
    }

    @Override
    public void meta(Map<String, Object> frame) {
        if (frame == null) {
            return;
        }
        meta.clear();
        frame.forEach((key, value) -> {
            if (!"ideas".equals(key) && !"type".equals(key) && !"success".equals(key)) {
                meta.put(key, value);
            }
        });
    }

    @Override
    public void item(Map<String, Object> row) {
        if (row == null) {
            return;
        }
        ideas.add(new LinkedHashMap<>(row));
    }

    public Map<String, Object> result() {
        Map<String, Object> out = new LinkedHashMap<>(meta);
        out.put("ideas", List.copyOf(ideas));
        return out;
    }

    public int ideaCount() {
        return ideas.size();
    }
}
