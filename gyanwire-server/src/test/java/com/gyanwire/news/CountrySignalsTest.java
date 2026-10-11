package com.gyanwire.news;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CountrySignalsTest {

    @Test
    void indiaWinsWhenTheHeadlineAlsoNamesAnotherCountry() {
        assertThat(CountrySignals.detect("FDA clears a device for clinics in India", "US", "fda.gov"))
                .isEqualTo("IN");
        assertThat(CountrySignals.detect("RBI issues a UPI circular", null, "rbi.org.in")).isEqualTo("IN");
    }

    @Test
    void worldwideHeadlinesKeepTheirOwnCountry() {
        assertThat(CountrySignals.detect("FDA approves a heart device", "GLOBAL", "example.com")).isEqualTo("US");
        assertThat(CountrySignals.detect("The U.S. clears a new listing", "GLOBAL", "example.com")).isEqualTo("US");
        assertThat(CountrySignals.detect("Ukraine opens a software office", "GLOBAL", "example.com")).isNull();
        assertThat(CountrySignals.detect("NHS reviews a new clinic tool", "GLOBAL", "example.com")).isEqualTo("GB");
        assertThat(CountrySignals.detect("Broker cuts equity fees", "IN", "livemint.com")).isEqualTo("IN");
        assertThat(CountrySignals.detect("Studio raises a series a", "GLOBAL", "techcrunch.com")).isNull();
        assertThat(CountrySignals.detect("Clinic opens in Sydney", "GLOBAL", "example.com")).isEqualTo("AU");
    }

    @Test
    void blankAndAllMeanWorldwide() {
        assertThat(CountrySignals.normalize(null)).isNull();
        assertThat(CountrySignals.normalize(" all ")).isNull();
        assertThat(CountrySignals.normalize("us")).isEqualTo("US");
        assertThatThrownBy(() -> CountrySignals.normalize("ZZ"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void optionsPutIndiaFirstAndCanKeepAnEmptySelection() {
        NewsRow india = new NewsRow().title("India launch").country("IN");
        NewsRow states = new NewsRow().title("FDA approval").country("US");
        NewsRow statesAgain = new NewsRow().title("Nasdaq listing").country("US");
        List<Map<String, Object>> options = CountrySignals.options(List.of(states, india, statesAgain));
        assertThat(options).extracting(item -> item.get("code")).containsExactly("IN", "US");
        CountrySignals.ensureOption(options, "GB");
        assertThat(options).extracting(item -> item.get("code")).containsExactly("IN", "US", "GB");
        assertThat(CountrySignals.matching(List.of(states, india), "US")).containsExactly(states);
        assertThat(CountrySignals.matching(List.of(states, india), null)).hasSize(2);
    }

    @Test
    void cappedFetchesKeepAWorldwideSource() {
        List<String> picked = SourceMix.pick(
                List.of("SEBI", "RBI", "Mint", "Hindu", "World", "BBC"),
                3,
                name -> "World".equals(name) || "BBC".equals(name) ? "GLOBAL" : "IN");
        assertThat(picked).containsExactly("SEBI", "RBI", "World");
        assertThat(SourceMix.pick(List.of("SEBI", "World"), 0, name -> "IN")).containsExactly("SEBI", "World");
    }
}
