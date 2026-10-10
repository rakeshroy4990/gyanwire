package com.gyanwire.llm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModelCatalogTest {

    @Test
    void threeTiersStayOnLunaAndSol() {
        assertThat(ModelCatalog.model(ModelTier.LIGHT)).isEqualTo("gpt-6-luna");
        assertThat(ModelCatalog.model(ModelTier.MAIN)).isEqualTo("gpt-6.1-sol");
        assertThat(ModelCatalog.model(ModelTier.DEEP)).isEqualTo(ModelCatalog.model(ModelTier.MAIN));
        assertThat(ModelCatalog.model(ModelTier.DEEP)).isNotEqualTo("gpt-6-astra");
        assertThat(ModelCatalog.effort(ModelTier.LIGHT)).isEqualTo("low");
        assertThat(ModelCatalog.effort(ModelTier.MAIN)).isEqualTo("medium");
        assertThat(ModelCatalog.effort(ModelTier.DEEP)).isEqualTo("high");
        assertThat(ModelCatalog.FX_INR_PER_USD).isEqualTo(96.5);
        assertThat(ModelCatalog.DAILY_CAP_INR).isEqualTo(300);
    }

    @Test
    void propertiesBindTiersAndRefuseAstraAsTheDefaultDeepModel() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "app.llm.router-enabled", "false",
                "app.llm.light-model", "gpt-6-luna",
                "app.llm.main-model", "gpt-6.1-sol",
                "app.llm.deep-model", "gpt-6-astra",
                "app.llm.fx-inr-per-usd", "96.5",
                "app.llm.daily-spend-cap-inr", "300"
        )));
        LlmProperties properties = binder.bind("app.llm", LlmProperties.class).get();
        assertThat(properties.modelFor(ModelTier.LIGHT)).isEqualTo("gpt-6-luna");
        assertThat(properties.modelFor(ModelTier.MAIN)).isEqualTo("gpt-6.1-sol");
        assertThat(properties.modelFor(ModelTier.DEEP)).isEqualTo("gpt-6.1-sol");
        assertThat(properties.isRouterEnabled()).isFalse();
    }
}
