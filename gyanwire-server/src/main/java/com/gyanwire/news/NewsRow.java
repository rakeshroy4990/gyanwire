package com.gyanwire.news;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class NewsRow {
    private UUID id;
    private String industry;
    private String sub;
    private String title;
    private String url;
    private String domain;
    private boolean resolved = true;
    private UUID sourceId;
    private String sourceName;
    private double sourceWeight = 1;
    private Instant publishedAt;
    private boolean dateEstimated;
    private Instant firstSeenAt;
    private String summary;
    private String signalType;
    private int indiaRelevance;
    private int specificity;
    private int opportunityScore;
    private String whyIdea;
    private String titleHash;
    private long simhash;
    private List<String> alsoCoveredBy = new ArrayList<>();
    private boolean sample;
    private String scoredBy;
    private String language = "en";
    private boolean weakSignal;

    public UUID id() { return id; }
    public NewsRow id(UUID id) { this.id = id; return this; }
    public String industry() { return industry; }
    public NewsRow industry(String industry) { this.industry = industry; return this; }
    public String sub() { return sub; }
    public NewsRow sub(String sub) { this.sub = sub; return this; }
    public String title() { return title; }
    public NewsRow title(String title) { this.title = title; return this; }
    public String url() { return url; }
    public NewsRow url(String url) { this.url = url; return this; }
    public String domain() { return domain; }
    public NewsRow domain(String domain) { this.domain = domain; return this; }
    public boolean resolved() { return resolved; }
    public NewsRow resolved(boolean resolved) { this.resolved = resolved; return this; }
    public UUID sourceId() { return sourceId; }
    public NewsRow sourceId(UUID sourceId) { this.sourceId = sourceId; return this; }
    public String sourceName() { return sourceName; }
    public NewsRow sourceName(String sourceName) { this.sourceName = sourceName; return this; }
    public double sourceWeight() { return sourceWeight; }
    public NewsRow sourceWeight(double sourceWeight) { this.sourceWeight = sourceWeight; return this; }
    public Instant publishedAt() { return publishedAt; }
    public NewsRow publishedAt(Instant publishedAt) { this.publishedAt = publishedAt; return this; }
    public boolean dateEstimated() { return dateEstimated; }
    public NewsRow dateEstimated(boolean dateEstimated) { this.dateEstimated = dateEstimated; return this; }
    public Instant firstSeenAt() { return firstSeenAt; }
    public NewsRow firstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; return this; }
    public String summary() { return summary; }
    public NewsRow summary(String summary) { this.summary = summary; return this; }
    public String signalType() { return signalType; }
    public NewsRow signalType(String signalType) { this.signalType = signalType; return this; }
    public int indiaRelevance() { return indiaRelevance; }
    public NewsRow indiaRelevance(int indiaRelevance) { this.indiaRelevance = indiaRelevance; return this; }
    public int specificity() { return specificity; }
    public NewsRow specificity(int specificity) { this.specificity = specificity; return this; }
    public int opportunityScore() { return opportunityScore; }
    public NewsRow opportunityScore(int opportunityScore) { this.opportunityScore = opportunityScore; return this; }
    public String whyIdea() { return whyIdea; }
    public NewsRow whyIdea(String whyIdea) { this.whyIdea = whyIdea; return this; }
    public String titleHash() { return titleHash; }
    public NewsRow titleHash(String titleHash) { this.titleHash = titleHash; return this; }
    public long simhash() { return simhash; }
    public NewsRow simhash(long simhash) { this.simhash = simhash; return this; }
    public List<String> alsoCoveredBy() { return alsoCoveredBy; }
    public NewsRow alsoCoveredBy(List<String> alsoCoveredBy) {
        this.alsoCoveredBy = alsoCoveredBy == null ? new ArrayList<>() : new ArrayList<>(alsoCoveredBy);
        return this;
    }
    public boolean sample() { return sample; }
    public NewsRow sample(boolean sample) { this.sample = sample; return this; }
    public String scoredBy() { return scoredBy; }
    public NewsRow scoredBy(String scoredBy) { this.scoredBy = scoredBy; return this; }
    public String language() { return language; }
    public NewsRow language(String language) { this.language = language; return this; }
    public boolean weakSignal() { return weakSignal; }
    public NewsRow weakSignal(boolean weakSignal) { this.weakSignal = weakSignal; return this; }
}
