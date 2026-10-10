package com.gyanwire.plans;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeekTaskTypeTest {

    @Test
    void mapsPhaseAndSkills() {
        assertThat(WeekTaskType.resolve("prove", List.of("code"))).isEqualTo("outreach");
        assertThat(WeekTaskType.resolve("decide", List.of())).isEqualTo("learning");
        assertThat(WeekTaskType.resolve("learn", List.of("python"))).isEqualTo("learning");
        assertThat(WeekTaskType.resolve("learn", List.of("writing"))).isEqualTo("writing");
        assertThat(WeekTaskType.resolve("build", List.of("design"))).isEqualTo("design");
        assertThat(WeekTaskType.resolve("build", List.of("sheets"))).isEqualTo("data");
        assertThat(WeekTaskType.resolve("build", List.of("ide"))).isEqualTo("coding");
        assertThat(WeekTaskType.resolve("build", List.of())).isEqualTo("writing");
    }

    @Test
    void sittingsAndBaseHours() {
        assertThat(WeekTaskType.sittings(10)).isEqualTo(2);
        assertThat(WeekTaskType.baseHours(10)).isEqualTo(10.0);
        assertThat(WeekTaskType.sittings(3)).isEqualTo(1);
        assertThat(WeekTaskType.baseHours(3)).isEqualTo(5.0);
    }
}
