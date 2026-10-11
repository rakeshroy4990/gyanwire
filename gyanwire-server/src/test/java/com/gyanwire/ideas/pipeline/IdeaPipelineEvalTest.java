package com.gyanwire.ideas.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IdeaPipelineEvalTest {

    @Test
    void fixturesPass() {
        var rows = IdeaPipelineEval.run();
        IdeaPipelineEval.print(rows);
        assertTrue(rows.stream().allMatch(IdeaPipelineEval.Row::passed), rows.toString());
    }
}
