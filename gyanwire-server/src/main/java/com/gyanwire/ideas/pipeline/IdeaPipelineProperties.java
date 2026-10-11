package com.gyanwire.ideas.pipeline;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ideas")
public class IdeaPipelineProperties {

    /**
     * When false, {@code POST /api/ideas/from-news} keeps the single-call generator.
     */
    private boolean pipelineEnabled;

    public boolean isPipelineEnabled() {
        return pipelineEnabled;
    }

    public void setPipelineEnabled(boolean pipelineEnabled) {
        this.pipelineEnabled = pipelineEnabled;
    }
}
