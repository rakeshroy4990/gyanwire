package com.gyanwire.ideas.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.gyanwire.ideas.pipeline.IdeaModels.BusinessIdea;

/**
 * Same-day social plan. Hours only, at most two days, one plan per platform.
 * Open-source tools are GitHub projects verified on 2026-10-11. Paid names are commercial products.
 */
public final class SocialMediaPlan {

    private static final List<String> ROLES = List.of("script", "video", "audio", "automation");

    private static final Map<String, String> ROLE_LABELS = Map.of(
            "script", "Script creation",
            "video", "Video creation",
            "audio", "Audio creation",
            "automation", "Automation");

    private static final Map<String, List<Map<String, Object>>> OPEN_SOURCE = Map.of(
            "script", List.of(
                    tool("ThiefMD", "kmwallio/ThiefMD", "https://github.com/kmwallio/ThiefMD",
                            "GPL-3.0", 184,
                            "Markdown / Fountain editor for drafting the spoken script and scene beats before you record."),
                    tool("Fountain Editor", "chuangcaleb/obsidian-fountain-editor",
                            "https://github.com/chuangcaleb/obsidian-fountain-editor",
                            "MIT", 89,
                            "Obsidian plugin for screenplay-style scripts when you already write notes there.")),
            "video", List.of(
                    tool("Shotcut", "mltframework/shotcut", "https://github.com/mltframework/shotcut",
                            "GPL-3.0", 15400,
                            "Desktop editor to cut A-roll, burn captions, and export Shorts / Reels / YouTube."),
                    tool("Kdenlive", "KDE/kdenlive", "https://github.com/KDE/kdenlive",
                            "GPL-3.0", 5834,
                            "Full timeline editor when the cut needs titles, layers, or more than a quick trim.")),
            "audio", List.of(
                    tool("Piper", "OHF-Voice/piper1-gpl", "https://github.com/OHF-Voice/piper1-gpl",
                            "GPL-3.0", 5825,
                            "Local text-to-speech: turn the finished script into a voiceover without a cloud API."),
                    tool("Pied", "Elleo/pied", "https://github.com/Elleo/pied",
                            "GPL-3.0", 297,
                            "Desktop front-end for Piper so you can pick a voice and export audio without a terminal.")),
            "automation", List.of(
                    tool("Huginn", "huginn/huginn", "https://github.com/huginn/huginn",
                            "MIT", 50041,
                            "Self-hosted agents that watch the story and trigger or schedule the publish step.")));

    private static final Map<String, List<Map<String, String>>> PAID = Map.of(
            "script", List.of(
                    paid("ChatGPT", "https://chatgpt.com",
                            "Draft the hook, three beats, on-screen lines, and caption from the news angle.",
                            "Free ChatGPT can brainstorm short lines. Listed as paid because Plus/Team unlock GPT-4o, higher rate limits, projects, and longer context for a full script + caption pass in one go."),
                    paid("Jasper", "https://www.jasper.ai",
                            "Brand-voice copy desk for post bodies, titles, and CTA variants.",
                            "No durable free writing workspace; the App / Business plans are what unlock templates, brand voice, and team drafts.")),
            "video", List.of(
                    paid("CapCut", "https://www.capcut.com",
                            "Templates, auto-captions, and vertical export for Shorts and Reels.",
                            "Free CapCut covers basic cuts. Listed as paid for Pro templates, cloud projects, and watermark-free pro effects used on polished Shorts."),
                    paid("Runway", "https://runwayml.com",
                            "Generative video when you need a B-roll shot you cannot film today.",
                            "Credits and Gen models sit behind paid plans; the free allowance is too small for a finished social clip."),
                    paid("Adobe Premiere", "https://www.adobe.com/products/premiere.html",
                            "Pro timeline editor for multi-track cuts, color, and broadcast-style export.",
                            "Premiere is Creative Cloud subscription software; there is no free full editor for this workflow.")),
            "audio", List.of(
                    paid("ElevenLabs", "https://elevenlabs.io",
                            "Studio voiceover or a cloned voice that matches your channel.",
                            "Free tier is low-character and watermarked for many commercial uses. Paid plans unlock higher quality, cloning, and commercial rights."),
                    paid("Adobe Podcast", "https://podcast.adobe.com",
                            "AI cleanup for noisy voice takes (denoise, enhance speech).",
                            "Enhance Speech and longer exports sit behind Adobe’s paid / Creative Cloud access; free use is limited.")),
            "automation", List.of(
                    paid("Buffer", "https://buffer.com",
                            "Queue the YouTube, Instagram, and LinkedIn posts for the same day.",
                            "Free Buffer is one channel and a tiny queue. Paid plans unlock multi-channel scheduling used in this plan."),
                    paid("Later", "https://later.com",
                            "Visual calendar for Reels, Stories, and feed posts.",
                            "Free Later is capped; paid unlocks the visual planner, Linkin.bio, and multi-profile queues."),
                    paid("Hootsuite", "https://www.hootsuite.com",
                            "Shared queue when more than one account posts in the same hour.",
                            "Team inboxes, approvals, and multi-account queues are paid; the free trial is not a lasting solo plan.")));

    private SocialMediaPlan() {
    }

    public static boolean isSocial(BusinessIdea idea) {
        return idea != null && "comparison_content".equals(idea.angleType());
    }

    /** False when a cached social plan still lacks tool URLs or use summaries. */
    public static boolean hasLinkedTools(Map<?, ?> plan) {
        if (plan == null || !(plan.get("platforms") instanceof List<?> platforms) || platforms.isEmpty()) {
            return false;
        }
        Object first = platforms.get(0);
        if (!(first instanceof Map<?, ?> platform) || !(platform.get("hours") instanceof List<?> hours) || hours.isEmpty()) {
            return false;
        }
        Object hour0 = hours.get(0);
        if (!(hour0 instanceof Map<?, ?> hour)) {
            return false;
        }
        if (!(hour.get("openSource") instanceof List<?> open) || open.isEmpty()) {
            return false;
        }
        if (!(open.get(0) instanceof Map<?, ?> os)
                || String.valueOf(os.get("url")).isBlank()
                || blank(os.get("summary"))) {
            return false;
        }
        if (!(hour.get("paid") instanceof List<?> paid) || paid.isEmpty()) {
            return false;
        }
        if (!(paid.get(0) instanceof Map<?, ?> row) || !String.valueOf(row.get("url")).startsWith("https://")) {
            return false;
        }
        return !blank(row.get("summary")) && !blank(row.get("whyPaid"));
    }

    private static boolean blank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    public static Map<String, Object> fromIdea(BusinessIdea idea, String newsTitle) {
        if (idea == null) {
            return Map.of();
        }
        String subject = subject(idea, newsTitle);
        String hook = hook(idea, newsTitle, subject);
        List<Map<String, Object>> platforms = List.of(
                platform("youtube", "YouTube", youtube(subject)),
                platform("instagram", "Instagram", instagram(subject)),
                platform("linkedin", "LinkedIn", linkedin(subject)));
        String who = idea.customer() == null || idea.customer().who() == null ? "" : idea.customer().who();
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("kind", "social");
        plan.put("durationDays", 1);
        plan.put("span", "4 hours. Finish today. Do not stretch past day 2.");
        plan.put("customer", who);
        plan.put("offer", idea.oneLiner() == null ? "" : idea.oneLiner());
        plan.put("platforms", platforms);
        plan.put("landingCopy", Map.of(
                "headline", subject,
                "subhead", hook,
                "cta", "Post it today"));
        return plan;
    }

    private static Map<String, Object> platform(String id, String label, List<String> tasks) {
        List<Map<String, Object>> hours = new ArrayList<>();
        for (int i = 0; i < ROLES.size(); i++) {
            hours.add(hour(i + 1, ROLES.get(i), tasks.get(i)));
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("label", label);
        row.put("hours", hours);
        return row;
    }

    private static Map<String, Object> hour(int index, String role, String task) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("hour", "Hour " + index);
        row.put("role", role);
        row.put("roleLabel", ROLE_LABELS.get(role));
        row.put("task", task);
        row.put("openSource", OPEN_SOURCE.get(role));
        row.put("paid", PAID.get(role));
        return row;
    }

    private static List<String> youtube(String subject) {
        return List.of(
                "Write a 60–90 second YouTube script for \"" + subject + "\". Open on the hook, then three beats.",
                "Cut a YouTube video or Short with on-screen text for that hook.",
                "Record or generate the voiceover and set music under the voice.",
                "Upload it, write the title and description, and schedule it for today or tomorrow.");
    }

    private static List<String> instagram(String subject) {
        return List.of(
                "Write a Reel hook, three on-screen lines, and the caption for \"" + subject + "\".",
                "Cut a 15–30 second Reel in 9:16 from that script.",
                "Add voice or a music bed, and burn in the captions.",
                "Schedule the Reel and one story that points to it. Do both the same day.");
    }

    private static List<String> linkedin(String subject) {
        return List.of(
                "Write a short LinkedIn post and a 45-second talking script for \"" + subject + "\".",
                "Cut a square clip, or export four carousel frames from the script.",
                "Clean the voice if this is a video. Skip music if the post is text only.",
                "Schedule the post and a first comment with the source link.");
    }

    private static String subject(BusinessIdea idea, String newsTitle) {
        if (idea.title() != null && !idea.title().isBlank()) {
            return idea.title().trim();
        }
        if (newsTitle != null && !newsTitle.isBlank()) {
            return newsTitle.trim();
        }
        return "this news";
    }

    private static String hook(BusinessIdea idea, String newsTitle, String subject) {
        if (idea.oneLiner() != null && !idea.oneLiner().isBlank()) {
            return idea.oneLiner().trim();
        }
        if (newsTitle != null && !newsTitle.isBlank()) {
            return newsTitle.trim();
        }
        return subject;
    }

    private static Map<String, Object> tool(
            String name, String repo, String url, String licence, int stars, String summary) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("repo", repo);
        row.put("url", url);
        row.put("licence", licence);
        row.put("stars", stars);
        row.put("summary", summary);
        return Map.copyOf(row);
    }

    private static Map<String, String> paid(String name, String url, String summary, String whyPaid) {
        return Map.of("name", name, "url", url, "summary", summary, "whyPaid", whyPaid);
    }
}
