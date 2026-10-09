package com.gyanwire.sources;

import com.gyanwire.persistence.FlowStore;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class SourcePackService {

    private final FlowStore store;

    public SourcePackService(FlowStore store) {
        this.store = store;
    }

    public void apply(UUID userId, SourcePackCatalog catalog) {
        try {
            Map<String, Double> weights = store.packWeights(userId);
            if (weights.isEmpty()) {
                catalog.clear();
            } else {
                catalog.use(weights);
            }
        } catch (Exception e) {
            catalog.clear();
        }
    }

    public void toggle(UUID userId, String packId, boolean enabled) {
        store.setPackEnabled(userId, packId, enabled);
    }
}
