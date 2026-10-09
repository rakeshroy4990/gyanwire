package com.gyanwire.research.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsTxtTest {

    @Test
    void disallowBlocksMatchingPath() {
        RobotsTxt rules = RobotsTxt.parse("""
                User-agent: *
                Disallow: /private
                Allow: /private/public-note
                """);

        assertThat(rules.allows("/private/secret")).isFalse();
        assertThat(rules.allows("/private/public-note")).isTrue();
        assertThat(rules.allows("/news")).isTrue();
    }
}
