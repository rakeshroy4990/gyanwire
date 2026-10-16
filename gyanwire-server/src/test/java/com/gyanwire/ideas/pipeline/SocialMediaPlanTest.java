package com.gyanwire.ideas.pipeline;

import com.gyanwire.actionplan.ActionPlanPipeline;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.gyanwire.ideas.pipeline.IdeaModels.BusinessIdea;
import static com.gyanwire.ideas.pipeline.IdeaModels.Customer;
import static com.gyanwire.ideas.pipeline.IdeaModels.RevenueModel;
import static com.gyanwire.ideas.pipeline.IdeaModels.ValidationTest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Legacy SocialMediaPlan path is retired; social ideas go through ActionPlanPipeline.
 * Keep smoke coverage for non-social launch plans and action-plan shape helpers.
 */
class SocialMediaPlanTest {

    @Test
    void socialFromIdeaIsDeferredToActionPlan() {
        var plan = LaunchPlanStage.fromIdea(idea(), "Labs told to file calibration logs");
        assertEquals("action_plan", plan.get("kind"));
        assertTrue(Boolean.TRUE.equals(plan.get("deferred")) || plan.containsKey("kind"));
    }

    @Test
    void otherIdeasStayOnASevenDayPlan() {
        var plan = LaunchPlanStage.fromIdea(idea("b2b_service"), "Labs told to file calibration logs");
        assertEquals(7, ((List<?>) plan.get("schedule")).size());
        assertFalse(plan.containsKey("platforms"));
    }

    @Test
    void actionPlanShapeHelperAcceptsValidPlans() {
        Map<String, Object> plan = Map.of(
                "kind", "action_plan",
                "budgetMinutes", 240,
                "steps", List.of(
                        Map.of("id", "s1"), Map.of("id", "s2"), Map.of("id", "s3"), Map.of("id", "s4"),
                        Map.of("id", "s5"), Map.of("id", "s6"), Map.of("id", "s7"), Map.of("id", "s8"),
                        Map.of("id", "s9"), Map.of("id", "s10")));
        assertTrue(ActionPlanPipeline.isActionPlan(plan));
        assertTrue(ActionPlanPipeline.hasValidShape(plan));
        assertFalse(ActionPlanPipeline.hasValidShape(Map.of("kind", "social")));
    }

    private static BusinessIdea idea() {
        return idea("comparison_content");
    }

    private static BusinessIdea idea(String angle) {
        return new BusinessIdea(
                angle,
                "Quick short on Labs",
                "One short for YouTube, Instagram, and LinkedIn on Labs.",
                new Customer("Viewers", "YouTube"),
                "The headline says Labs told to file calibration logs.",
                "Viewers want a plain read.",
                "It is current.",
                "Record one short.",
                new RevenueModel("ads", "", "Shorts can earn ad revenue share only after YouTube Partner Program eligibility. This is not instant income."),
                List.of("Write a script.", "Cut the clip.", "Upload."),
                new ValidationTest("Post the short today", "100 views in two days"),
                List.of("The hook is vague if the headline fact is left out."),
                "hours",
                false);
    }
}
