package com.gyanwire.ideas.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.gyanwire.llm.ModelTier;

import java.util.UUID;
import java.util.function.Function;

final class StageCalls {

    private StageCalls() {
    }

    static JsonNode completeValidated(
            IdeaStageClient client,
            UUID userId,
            String promptVersion,
            String feature,
            String system,
            String user,
            ModelTier tier,
            Function<JsonNode, String> validate,
            int maxAttempts
    ) {
        String extra = "";
        JsonNode last = null;
        int attempts = Math.max(1, maxAttempts);
        for (int i = 0; i < attempts; i++) {
            last = client.complete(userId, promptVersion, feature, system, user + extra, tier);
            String error = last == null ? "missing json" : validate.apply(last);
            PipelineLog.stage(feature, user + extra, last == null ? error : last);
            if (error == null) {
                if (i > 0) {
                    PipelineLog.attempt(feature, i + 1, attempts, null);
                }
                return last;
            }
            if (i + 1 < attempts) {
                PipelineLog.attempt(feature, i + 1, attempts, error);
            }
            extra = "\nValidation error: " + error + "\nReturn JSON that fixes it.";
        }
        PipelineLog.stageFailed(feature);
        return null;
    }
}
