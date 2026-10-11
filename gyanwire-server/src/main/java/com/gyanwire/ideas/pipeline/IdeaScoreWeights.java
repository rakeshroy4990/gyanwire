package com.gyanwire.ideas.pipeline;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class IdeaScoreWeights {

    private final Map<String, Double> weights;

    private IdeaScoreWeights(Map<String, Double> weights) {
        this.weights = weights;
    }

    public static IdeaScoreWeights load() {
        Properties properties = new Properties();
        try (InputStream in = IdeaScoreWeights.class.getResourceAsStream("/ideas/score-weights.properties")) {
            if (in == null) {
                throw new IllegalStateException("score-weights.properties missing");
            }
            properties.load(in);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load score weights", ex);
        }
        Map<String, Double> weights = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            weights.put(key, Double.parseDouble(properties.getProperty(key).trim()));
        }
        return new IdeaScoreWeights(weights);
    }

    public double weight(String key) {
        return weights.getOrDefault(key, 0d);
    }

    public Map<String, Double> weights() {
        return weights;
    }

    public double weighted(Map<String, Integer> scores) {
        double totalWeight = 0;
        double total = 0;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            Integer score = scores.get(entry.getKey());
            if (score == null) {
                continue;
            }
            totalWeight += entry.getValue();
            total += entry.getValue() * score;
        }
        if (totalWeight <= 0) {
            return 1;
        }
        return total / totalWeight;
    }
}
