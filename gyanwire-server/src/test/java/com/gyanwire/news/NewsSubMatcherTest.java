package com.gyanwire.news;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewsSubMatcherTest {

    @Test
    void keepsExactSubTagEvenWithoutKeywords() {
        NewsRow tagged = new NewsRow().sub("Cloud").title("Vendor announces platform update");
        assertThat(NewsSubMatcher.matches("IT", "Cloud", tagged)).isTrue();
    }

    @Test
    void rejectsDifferentSubTag() {
        NewsRow cyber = new NewsRow().sub("Cybersecurity").title("AWS launches new cloud region");
        assertThat(NewsSubMatcher.matches("IT", "Cloud", cyber)).isFalse();
    }

    @Test
    void parentRowsNeedSubKeywords() {
        NewsRow cloud = new NewsRow().title("Azure rolls out serverless Kubernetes update");
        NewsRow generic = new NewsRow().title("Indian IT services outlook for FY27");
        assertThat(NewsSubMatcher.matches("IT", "Cloud", cloud)).isTrue();
        assertThat(NewsSubMatcher.matches("IT", "Cloud", generic)).isFalse();
        assertThat(NewsSubMatcher.matches("IT", "Semiconductors", cloud)).isFalse();
    }

    @Test
    void shareMarketEvDoesNotTakeGenericMarketWrap() {
        NewsRow wrap = new NewsRow().title("Nifty ends higher; bank stocks lead gains");
        NewsRow ev = new NewsRow().title("Tesla delivery beat lifts EV stock sentiment");
        assertThat(NewsSubMatcher.matches("Share Market", "EV", wrap)).isFalse();
        assertThat(NewsSubMatcher.matches("Share Market", "EV", ev)).isTrue();
        assertThat(NewsSubMatcher.matches("Share Market", "Banking", wrap)).isTrue();
    }
}
