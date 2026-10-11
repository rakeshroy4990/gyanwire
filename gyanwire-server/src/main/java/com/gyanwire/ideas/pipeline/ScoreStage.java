package com.gyanwire.ideas.pipeline;

import java.util.List;
import java.util.Map;

import static com.gyanwire.ideas.pipeline.IdeaModels.BusinessIdea;
import static com.gyanwire.ideas.pipeline.IdeaModels.NewsSignals;
import static com.gyanwire.ideas.pipeline.IdeaModels.ScoredIdea;

public final class ScoreStage {

    private final IdeaScoreCalculator calculator;

    public ScoreStage(IdeaScoreWeights weights) {
        this.calculator = new IdeaScoreCalculator(weights);
    }

    public List<ScoredIdea> run(List<BusinessIdea> ideas, NewsSignals signals, Map<String, Object> profile) {
        List<ScoredIdea> scored = calculator.score(ideas, signals, profile);
        scored.sort((a, b) -> {
            int priority = Integer.compare(
                    AngleType.displayPriority(a.idea().angleType()),
                    AngleType.displayPriority(b.idea().angleType()));
            if (priority != 0) {
                return priority;
            }
            return Double.compare(b.score().weighted(), a.score().weighted());
        });
        Integer top = scored.isEmpty() ? null : scored.get(0).score().stored();
        PipelineLog.score(ideas == null ? 0 : ideas.size(), scored.size(), top);
        PipelineLog.stage("score", ideas == null ? 0 : ideas.size(), scored.size());
        return scored;
    }
}
