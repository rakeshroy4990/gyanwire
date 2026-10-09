package com.gyanwire.research.engine;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CuriosityRankTest {

    @Test
    void prefersCuriousHooksOverMarketWraps() {
        Map<String, Object> wrap = new HashMap<>();
        wrap.put("title", "Sensex ends higher as Nifty closes above key level: market wrap");
        wrap.put("score", 90);

        Map<String, Object> curious = new HashMap<>();
        curious.put("title", "Why TCS quietly open-sourced a surprising AI demo nobody expected");
        curious.put("score", 70);

        assertThat(CuriosityRank.boost(curious)).isGreaterThan(CuriosityRank.boost(wrap));
        assertThat(CuriosityRank.looksGeneric(String.valueOf(wrap.get("title")))).isTrue();
        assertThat(CuriosityRank.hookScore(String.valueOf(curious.get("title")))).isGreaterThan(0.3);
    }
}
