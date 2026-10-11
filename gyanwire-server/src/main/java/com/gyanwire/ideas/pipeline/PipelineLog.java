package com.gyanwire.ideas.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.gyanwire.ideas.pipeline.IdeaModels.NewsSignals;
import static com.gyanwire.ideas.pipeline.IdeaModels.OpportunityGate;

/**
 * INFO breadcrumbs for the idea pipeline; DEBUG dumps stage payloads.
 */
final class PipelineLog {

    private static final Logger log = LoggerFactory.getLogger("com.gyanwire.ideas.pipeline");

    private PipelineLog() {
    }

    static void start(UUID userId, String title, String industry, boolean force) {
        log.info(
                "idea-pipeline start userId={} title={} industry={} force={}",
                userId,
                shortText(title, 80),
                blank(industry),
                force);
    }

    static void cacheHit(UUID runId, int ideaCount) {
        log.info("idea-pipeline cache-hit runId={} ideas={}", runId, ideaCount);
    }

    static void signals(NewsSignals signals) {
        if (signals == null) {
            log.info("idea-pipeline stage=signals status=failed");
            return;
        }
        log.info(
                "idea-pipeline stage=signals status=ok eventType={} urgency={} groups={} facts={} claims={}",
                blank(signals.eventType()),
                signals.timeWindow() == null ? "-" : blank(signals.timeWindow().urgency()),
                size(signals.affectedGroups()),
                size(signals.confirmedFacts()),
                size(signals.unconfirmedClaims()));
    }

    static void gate(OpportunityGate gate) {
        if (gate == null) {
            log.info("idea-pipeline stage=gate status=failed");
            return;
        }
        log.info(
                "idea-pipeline stage=gate proceed={} confidence={} reason={}",
                gate.proceed(),
                gate.confidence(),
                shortText(gate.reason(), 160));
    }

    static void angles(Collection<String> angles) {
        log.info(
                "idea-pipeline stage=angles count={} angles={}",
                size(angles),
                join(angles));
    }

    static void build(int valid, int rejected) {
        log.info("idea-pipeline stage=idea-build valid={} rejected={}", valid, rejected);
    }

    static void critic(int kept, int removed) {
        log.info("idea-pipeline stage=critic kept={} removed={}", kept, removed);
    }

    static void score(int input, int output, Integer topScore) {
        if (topScore == null) {
            log.info("idea-pipeline stage=score input={} output={}", input, output);
            return;
        }
        log.info("idea-pipeline stage=score input={} output={} topScore={}", input, output, topScore);
    }

    static void launchPlan(UUID ideaId, boolean cached, boolean generated) {
        log.info("idea-pipeline stage=launch-plan ideaId={} cached={} generated={}", ideaId, cached, generated);
    }

    static void done(UUID runId, int ideas, boolean cached, String emptyReason) {
        if (emptyReason == null || emptyReason.isBlank()) {
            log.info("idea-pipeline done runId={} ideas={} cached={}", runId, ideas, cached);
            return;
        }
        log.info(
                "idea-pipeline done runId={} ideas={} cached={} emptyReason={}",
                runId,
                ideas,
                cached,
                shortText(emptyReason, 160));
    }

    static void unread(String reason) {
        log.info("idea-pipeline unread reason={}", shortText(reason, 160));
    }

    static void attempt(String stage, int attempt, int maxAttempts, String error) {
        if (error == null) {
            log.info("idea-pipeline stage={} attempt={}/{} status=ok", stage, attempt, maxAttempts);
            return;
        }
        log.info(
                "idea-pipeline stage={} attempt={}/{} status=retry error={}",
                stage,
                attempt,
                maxAttempts,
                shortText(error, 120));
    }

    static void stageFailed(String stage) {
        log.info("idea-pipeline stage={} status=failed", stage);
    }

    /** Full stage I/O — DEBUG only. */
    static void stage(String name, Object input, Object output) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug("stage={} input={} output={}", name, trim(input), trim(output));
    }

    static void removed(String angle, String reason) {
        log.info("idea-pipeline removed angle={} reason={}", shortText(angle, 40), shortText(reason, 160));
    }

    private static String join(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream()
                .map(v -> shortText(v, 40))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static int size(Collection<?> values) {
        return values == null ? 0 : values.size();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private static String shortText(String value, int max) {
        if (value == null) {
            return "-";
        }
        String cleaned = value.replace('\n', ' ').trim();
        if (cleaned.isEmpty()) {
            return "-";
        }
        return cleaned.length() > max ? cleaned.substring(0, max) + "…" : cleaned;
    }

    private static String trim(Object value) {
        String text = String.valueOf(value);
        return text.length() > 4000 ? text.substring(0, 4000) + "…" : text;
    }
}
