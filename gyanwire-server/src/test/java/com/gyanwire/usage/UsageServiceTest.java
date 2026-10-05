package com.gyanwire.usage;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UsageServiceTest {

    @Test
    void evaluateSearchLimitBlocksWhenUsedReachesLimit() {
        Map<String, Object> evaluation = UsageService.evaluateSearchLimit(5, 5);
        assertThat(evaluation.get("allowed")).isEqualTo(false);
        assertThat(evaluation.get("remaining")).isEqualTo(0L);
    }

    @Test
    void evaluateSearchLimitAllowsWhenUnderLimit() {
        Map<String, Object> evaluation = UsageService.evaluateSearchLimit(2, 5);
        assertThat(evaluation.get("allowed")).isEqualTo(true);
        assertThat(evaluation.get("remaining")).isEqualTo(3L);
    }
}
