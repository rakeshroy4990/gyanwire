package com.gyanwire.news;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignalRulesTest {

    @Test
    void marketFlowCommentaryIsFyi() {
        assertThat(SignalRules.detect(
                "FIIs dump Indian stocks for eight straight weeks: Is a reversal on the cards or more pain ahead?"))
                .isEqualTo("fyi");
        assertThat(SignalRules.detect("FPIs selling Indian equities for a sixth week"))
                .isEqualTo("fyi");
    }

    @Test
    void bureaucraticProcessWithoutNewRuleIsFyi() {
        assertThat(SignalRules.detect(
                "National Formulary of India lists drugs not approved by CDSCO, DCGI seeks explanation from IPC"))
                .isEqualTo("fyi");
        assertThat(SignalRules.detect("Regulator seeks clarification from the company on its filing"))
                .isEqualTo("fyi");
    }

    @Test
    void notApprovedDoesNotBecomeApproval() {
        assertThat(SignalRules.detect("Panel lists drugs not approved by CDSCO for hospital use"))
                .isEqualTo("fyi");
        assertThat(SignalRules.detect("CDSCO approves a paediatric heart valve"))
                .isEqualTo("approval");
    }

    @Test
    void marketPlumbingAndCheckDetailsAreFyiNotLaunch() {
        assertThat(SignalRules.detect(
                "Sebi notifies new settlement rule; unveils formula, fast-track route. Check details"))
                .isEqualTo("fyi");
        assertThat(SignalRules.detect("NSE announces trading holiday for Diwali Muhurat session"))
                .isEqualTo("fyi");
        assertThat(SignalRules.detect("Sebi’s CAS guidelines likely within a week, says chief Tuhin Kanta Pandey"))
                .isEqualTo("fyi");
        assertThat(SignalRules.detect("Zerodha launches a new mutual fund screener for retail investors"))
                .isEqualTo("launch");
    }

    @Test
    void commodityCommentaryIsFyi() {
        assertThat(SignalRules.detect(
                "Brent commands a war premium as Middle East supply risks keep oil"))
                .isEqualTo("fyi");
        assertThat(SignalRules.detect("WTI holds a risk premium as traders watch crude"))
                .isEqualTo("fyi");
        assertThat(SignalRules.detect("HPCL launches a prepaid fuel card for truck fleets"))
                .isEqualTo("launch");
    }

    @Test
    void plantInspectionObservationsAreFyi() {
        assertThat(SignalRules.detect(
                "Dr Reddy’s Laboratories receives two observations for Andhra Pradesh facility"))
                .isEqualTo("fyi");
        assertThat(SignalRules.detect("Sun Pharma gets Form 483 for its Halol plant"))
                .isEqualTo("fyi");
        assertThat(SignalRules.detect("USFDA completes inspection of the Goa facility"))
                .isEqualTo("fyi");
        assertThat(SignalRules.detect("Dr Reddy’s recalls a batch after the plant observations"))
                .isEqualTo("shortage");
        assertThat(SignalRules.detect("CDSCO approves a paediatric heart valve"))
                .isEqualTo("approval");
    }

    @Test
    void fyiHasNoActionability() {
        assertThat(SignalRules.isFyi("fyi")).isTrue();
        assertThat(SignalRules.actionability("fyi")).isZero();
        assertThat(SignalRules.whyIdea("fyi", "Share Market")).contains("FYI only");
    }
}
