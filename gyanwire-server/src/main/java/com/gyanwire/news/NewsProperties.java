package com.gyanwire.news;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gyanwire.news")
public class NewsProperties {

    private final Ingest ingest = new Ingest();
    private final Classify classify = new Classify();
    private final Rank rank = new Rank();
    private final Alert alert = new Alert();
    private final Seed seed = new Seed();

    public Ingest getIngest() {
        return ingest;
    }

    public Classify getClassify() {
        return classify;
    }

    public Rank getRank() {
        return rank;
    }

    public Alert getAlert() {
        return alert;
    }

    public Seed getSeed() {
        return seed;
    }

    public static class Ingest {
        private boolean enabled = true;
        private boolean onStartup = false;
        private int maxSourcesPerIndustry = 0;
        private int connectTimeoutMs = 8000;
        private int readTimeoutMs = 12000;
        private int redirectTimeoutMs = 4000;
        private int hostSpacingMs = 1000;
        private int onDemandTimeoutSeconds = 12;
        private int pollIntervalMinutes = 30;
        private String userAgent = "GyanwireBot/1.0 (+local research tool)";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isOnStartup() {
            return onStartup;
        }

        public void setOnStartup(boolean onStartup) {
            this.onStartup = onStartup;
        }

        public int getMaxSourcesPerIndustry() {
            return maxSourcesPerIndustry;
        }

        public void setMaxSourcesPerIndustry(int maxSourcesPerIndustry) {
            this.maxSourcesPerIndustry = maxSourcesPerIndustry;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public int getRedirectTimeoutMs() {
            return redirectTimeoutMs;
        }

        public void setRedirectTimeoutMs(int redirectTimeoutMs) {
            this.redirectTimeoutMs = redirectTimeoutMs;
        }

        public int getHostSpacingMs() {
            return hostSpacingMs;
        }

        public void setHostSpacingMs(int hostSpacingMs) {
            this.hostSpacingMs = hostSpacingMs;
        }

        public int getOnDemandTimeoutSeconds() {
            return onDemandTimeoutSeconds;
        }

        public void setOnDemandTimeoutSeconds(int onDemandTimeoutSeconds) {
            this.onDemandTimeoutSeconds = onDemandTimeoutSeconds;
        }

        public int getPollIntervalMinutes() {
            return pollIntervalMinutes;
        }

        public void setPollIntervalMinutes(int pollIntervalMinutes) {
            this.pollIntervalMinutes = pollIntervalMinutes;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }
    }

    public static class Classify {
        private boolean llm = false;

        public boolean isLlm() {
            return llm;
        }

        public void setLlm(boolean llm) {
            this.llm = llm;
        }
    }

    public static class Rank {
        private int recency = 25;
        private int signal = 25;
        private int specificity = 20;
        private int india = 15;
        private int actionability = 15;
        private int minScore = 40;
        private int freshFullDays = 3;
        private int rulesCap = 55;
        private int indiaBoost = 8;
        private int intentBoost = 8;
        private int intentDemote = 12;
        private int cacheSeconds = 60;
        private String windows = "14,30,60";

        public int getRecency() {
            return recency;
        }

        public void setRecency(int recency) {
            this.recency = recency;
        }

        public int getSignal() {
            return signal;
        }

        public void setSignal(int signal) {
            this.signal = signal;
        }

        public int getSpecificity() {
            return specificity;
        }

        public void setSpecificity(int specificity) {
            this.specificity = specificity;
        }

        public int getIndia() {
            return india;
        }

        public void setIndia(int india) {
            this.india = india;
        }

        public int getActionability() {
            return actionability;
        }

        public void setActionability(int actionability) {
            this.actionability = actionability;
        }

        public int getMinScore() {
            return minScore;
        }

        public void setMinScore(int minScore) {
            this.minScore = minScore;
        }

        public int getFreshFullDays() {
            return freshFullDays;
        }

        public void setFreshFullDays(int freshFullDays) {
            this.freshFullDays = freshFullDays;
        }

        public int getRulesCap() {
            return rulesCap;
        }

        public void setRulesCap(int rulesCap) {
            this.rulesCap = rulesCap;
        }

        public int getIndiaBoost() {
            return indiaBoost;
        }

        public void setIndiaBoost(int indiaBoost) {
            this.indiaBoost = indiaBoost;
        }

        public int getIntentBoost() {
            return intentBoost;
        }

        public void setIntentBoost(int intentBoost) {
            this.intentBoost = intentBoost;
        }

        public int getIntentDemote() {
            return intentDemote;
        }

        public void setIntentDemote(int intentDemote) {
            this.intentDemote = intentDemote;
        }

        public int getCacheSeconds() {
            return cacheSeconds;
        }

        public void setCacheSeconds(int cacheSeconds) {
            this.cacheSeconds = cacheSeconds;
        }

        public String getWindows() {
            return windows;
        }

        public void setWindows(String windows) {
            this.windows = windows;
        }

        public int[] windowDays() {
            String raw = windows == null || windows.isBlank() ? "14,30,60" : windows;
            String[] parts = raw.split(",");
            int[] out = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                out[i] = Integer.parseInt(parts[i].trim().replaceAll("[^0-9]", ""));
            }
            return out;
        }
    }

    public static class Alert {
        private int medianAgeDays = 10;
        private int failureStreak = 3;

        public int getMedianAgeDays() {
            return medianAgeDays;
        }

        public void setMedianAgeDays(int medianAgeDays) {
            this.medianAgeDays = medianAgeDays;
        }

        public int getFailureStreak() {
            return failureStreak;
        }

        public void setFailureStreak(int failureStreak) {
            this.failureStreak = failureStreak;
        }
    }

    public static class Seed {
        private boolean allowed = false;
        private boolean onEmpty = false;

        public boolean isAllowed() {
            return allowed;
        }

        public void setAllowed(boolean allowed) {
            this.allowed = allowed;
        }

        public boolean isOnEmpty() {
            return onEmpty;
        }

        public void setOnEmpty(boolean onEmpty) {
            this.onEmpty = onEmpty;
        }
    }
}
