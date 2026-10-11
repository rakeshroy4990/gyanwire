package com.gyanwire.ideas.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.llm.ModelTier;
import com.gyanwire.research.engine.LlmService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.gyanwire.ideas.pipeline.IdeaModels.NewsSignals;
import static com.gyanwire.ideas.pipeline.IdeaModels.ParsedIdeas;

public final class IdeaBuilderStage {

    private final ObjectMapper mapper;

    public IdeaBuilderStage(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ParsedIdeas run(
            IdeaStageClient client,
            UUID userId,
            NewsSignals signals,
            List<String> angles,
            Map<String, Object> profile
    ) {
        Set<String> allowed = Set.copyOf(angles);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("signals", signals);
        payload.put("angles", angles);
        payload.put("profile", profile == null ? Map.of() : profile);
        payload.put("rule", "Return one idea per angle. Distinct angleType values only. Fewer ideas is better than a duplicate.");
        String user;
        try {
            user = mapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return new ParsedIdeas(List.of(), List.of());
        }
        JsonNode last = null;
        String extra = "";
        ParsedIdeas best = new ParsedIdeas(List.of(), List.of());
        int maxAttempts = 3;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            last = client.complete(
                    userId,
                    "idea-build.v1",
                    "idea-build",
                    LlmService.prompt("idea-build.v1.txt"),
                    user + extra,
                    ModelTier.MAIN);
            ParsedIdeas parsed = IdeaPipelineSchemas.parseIdeas(last, signals, allowed);
            PipelineLog.stage("idea-build", user + extra, last);
            if (parsed.rejected().isEmpty() && !parsed.valid().isEmpty()) {
                if (attempt > 0) {
                    PipelineLog.attempt("idea-build", attempt + 1, maxAttempts, null);
                }
                PipelineLog.build(parsed.valid().size(), parsed.rejected().size());
                return parsed;
            }
            if (parsed.valid().size() > best.valid().size()) {
                best = parsed;
            }
            String error = describe(parsed);
            if (attempt + 1 < maxAttempts) {
                PipelineLog.attempt("idea-build", attempt + 1, maxAttempts, error);
            }
            if (parsed.valid().isEmpty() && parsed.rejected().isEmpty()) {
                extra = "\nValidation error: ideas required\nReturn JSON that fixes it.";
                continue;
            }
            extra = "\nValidation error: " + error + "\nFix only the rejected ideas. Keep distinct angleType values.";
        }
        PipelineLog.build(best.valid().size(), best.rejected().size());
        return best;
    }

    private static String describe(ParsedIdeas parsed) {
        StringBuilder text = new StringBuilder();
        for (var removal : parsed.rejected()) {
            if (!text.isEmpty()) {
                text.append("; ");
            }
            text.append(removal.angleType()).append(" ").append(removal.reason());
        }
        return text.isEmpty() ? "ideas required" : text.toString();
    }
}
