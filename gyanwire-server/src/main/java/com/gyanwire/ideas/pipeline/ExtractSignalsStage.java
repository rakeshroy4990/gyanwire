package com.gyanwire.ideas.pipeline;

import com.gyanwire.llm.ModelTier;
import com.gyanwire.research.engine.LlmService;

import java.util.UUID;

import static com.gyanwire.ideas.pipeline.IdeaModels.NewsSignals;

public final class ExtractSignalsStage {

    public NewsSignals run(IdeaStageClient client, UUID userId, String title, String article) {
        String user = "Audience country: India\nHeadline: " + title + "\n\nArticle:\n" + truncate(article);
        var node = StageCalls.completeValidated(
                client,
                userId,
                "idea-signals.v1",
                "idea-signals",
                LlmService.prompt("idea-signals.v1.txt"),
                user,
                ModelTier.LIGHT,
                IdeaPipelineSchemas::validateSignals,
                2);
        if (node == null) {
            return null;
        }
        return IdeaPipelineSchemas.parseSignals(node);
    }

    private static String truncate(String article) {
        if (article == null) {
            return "";
        }
        String cleaned = article.trim();
        return cleaned.length() > 6000 ? cleaned.substring(0, 6000) : cleaned;
    }
}
