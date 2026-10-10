package com.gyanwire.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {

    private boolean routerEnabled = false;
    private boolean shadow = false;
    private double fxInrPerUsd = ModelCatalog.FX_INR_PER_USD;
    private double dailySpendCapInr = ModelCatalog.DAILY_CAP_INR;
    private String lightModel = ModelCatalog.LIGHT_MODEL;
    private String mainModel = ModelCatalog.MAIN_MODEL;
    private String deepModel = ModelCatalog.DEEP_MODEL;
    private Stages stage = new Stages();

    public boolean isRouterEnabled() {
        return routerEnabled;
    }

    public void setRouterEnabled(boolean routerEnabled) {
        this.routerEnabled = routerEnabled;
    }

    public boolean isShadow() {
        return shadow;
    }

    public void setShadow(boolean shadow) {
        this.shadow = shadow;
    }

    public double getFxInrPerUsd() {
        return fxInrPerUsd <= 0 ? ModelCatalog.FX_INR_PER_USD : fxInrPerUsd;
    }

    public void setFxInrPerUsd(double fxInrPerUsd) {
        this.fxInrPerUsd = fxInrPerUsd;
    }

    public double getDailySpendCapInr() {
        return dailySpendCapInr <= 0 ? ModelCatalog.DAILY_CAP_INR : dailySpendCapInr;
    }

    public void setDailySpendCapInr(double dailySpendCapInr) {
        this.dailySpendCapInr = dailySpendCapInr;
    }

    public String getLightModel() {
        return blank(lightModel) ? ModelCatalog.LIGHT_MODEL : lightModel.trim();
    }

    public void setLightModel(String lightModel) {
        this.lightModel = lightModel;
    }

    public String getMainModel() {
        return blank(mainModel) ? ModelCatalog.MAIN_MODEL : mainModel.trim();
    }

    public void setMainModel(String mainModel) {
        this.mainModel = mainModel;
    }

    public String getDeepModel() {
        return blank(deepModel) || deepModel.toLowerCase().contains("astra")
                ? ModelCatalog.DEEP_MODEL
                : deepModel.trim();
    }

    public void setDeepModel(String deepModel) {
        this.deepModel = deepModel;
    }

    public Stages getStage() {
        return stage == null ? new Stages() : stage;
    }

    public void setStage(Stages stage) {
        this.stage = stage;
    }

    public String modelFor(ModelTier tier) {
        if (tier == null) {
            return getLightModel();
        }
        return switch (tier) {
            case LIGHT -> getLightModel();
            case MAIN -> getMainModel();
            case DEEP -> getDeepModel();
        };
    }

    public boolean stageEnabled(String name) {
        Stages stages = getStage();
        return switch (name == null ? "" : name) {
            case "sharpen" -> stages.sharpen;
            case "whylines" -> stages.whylines;
            case "rerank" -> stages.rerank;
            case "plan_a" -> stages.planA;
            case "plan_b" -> stages.planB;
            case "whatif" -> stages.whatif;
            case "news_classify" -> stages.newsClassify;
            default -> false;
        };
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public static class Stages {
        private boolean sharpen;
        private boolean whylines;
        private boolean rerank;
        private boolean planA;
        private boolean planB;
        private boolean whatif;
        private boolean newsClassify;

        public boolean isSharpen() {
            return sharpen;
        }

        public void setSharpen(boolean sharpen) {
            this.sharpen = sharpen;
        }

        public boolean isWhylines() {
            return whylines;
        }

        public void setWhylines(boolean whylines) {
            this.whylines = whylines;
        }

        public boolean isRerank() {
            return rerank;
        }

        public void setRerank(boolean rerank) {
            this.rerank = rerank;
        }

        public boolean isPlanA() {
            return planA;
        }

        public void setPlanA(boolean planA) {
            this.planA = planA;
        }

        public boolean isPlanB() {
            return planB;
        }

        public void setPlanB(boolean planB) {
            this.planB = planB;
        }

        public boolean isWhatif() {
            return whatif;
        }

        public void setWhatif(boolean whatif) {
            this.whatif = whatif;
        }

        public boolean isNewsClassify() {
            return newsClassify;
        }

        public void setNewsClassify(boolean newsClassify) {
            this.newsClassify = newsClassify;
        }
    }
}
