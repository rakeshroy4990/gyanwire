package com.gyanwire.ideas.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.gyanwire.ideas.pipeline.IdeaModels.BusinessIdea;
import static com.gyanwire.ideas.pipeline.IdeaModels.CriticResult;
import static com.gyanwire.ideas.pipeline.IdeaModels.NewsSignals;
import static com.gyanwire.ideas.pipeline.IdeaModels.Removal;

/**
 * Drops duplicate formats, generic buyers, and B2B ideas the news does not support.
 * Unconfirmed facts are flagged on the idea, not deleted.
 */
public final class IdeaCriticRules {

    private static final Set<String> STOP = Set.of(
            "this", "that", "with", "from", "your", "their", "they", "will", "have", "into",
            "about", "india", "indian", "buyers", "buyer", "page", "news", "launch");

    private IdeaCriticRules() {
    }

    public static CriticResult review(List<BusinessIdea> ideas, NewsSignals signals) {
        List<BusinessIdea> kept = new ArrayList<>();
        List<Removal> removed = new ArrayList<>();
        if (ideas == null) {
            return new CriticResult(List.of(), List.of());
        }
        for (BusinessIdea raw : ideas) {
            if (raw == null) {
                continue;
            }
            BusinessIdea idea = IdeaPipelineSchemas.tagUnconfirmed(raw, signals);
            String reason = rejectReason(idea, signals, kept);
            if (reason != null) {
                removed.add(new Removal(idea.angleType(), idea.title(), reason));
                continue;
            }
            kept.add(idea);
        }
        return new CriticResult(List.copyOf(kept), List.copyOf(removed));
    }

    private static String rejectReason(BusinessIdea idea, NewsSignals signals, List<BusinessIdea> kept) {
        if (TextCheck.genericWho(idea.customer() == null ? "" : idea.customer().who())) {
            return "customer is generic";
        }
        if (TextCheck.genericTrigger(idea.trigger())) {
            return "trigger is generic";
        }
        if (TextCheck.banned(idea.blob()) != null) {
            return "placeholder copy";
        }
        if ("b2b_service".equals(idea.angleType())
                && (signals == null || !AngleType.hasBusinessGroup(signals.affectedGroups()))) {
            return "no affected business group";
        }
        for (BusinessIdea prior : kept) {
            if (prior.angleType().equals(idea.angleType())) {
                return "duplicate angleType";
            }
            if (overlap(prior.blob(), idea.blob()) >= 0.72) {
                return "same offer as another idea, only the format changed";
            }
        }
        return null;
    }

    static double overlap(String leftText, String rightText) {
        Set<String> left = tokens(leftText);
        Set<String> right = tokens(rightText);
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        int shared = 0;
        for (String token : left) {
            if (right.contains(token)) {
                shared++;
            }
        }
        return shared / (double) Math.min(left.size(), right.size());
    }

    private static Set<String> tokens(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) {
            return out;
        }
        for (String part : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (part.length() > 3 && !STOP.contains(part)) {
                out.add(part);
            }
        }
        return out;
    }
}
