package com.gyanwire.ideas.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.gyanwire.ideas.pipeline.IdeaModels.BusinessIdea;

/**
 * @deprecated Replaced by {@code com.gyanwire.actionplan.ActionPlanPipeline} (short_vertical ActionPlan).
 * Kept temporarily for reference; do not call from new code.
 */
@Deprecated
public final class SocialMediaPlan {

    private static final List<String> ROLES = List.of("script", "video", "edit", "automation");

    private static final Map<String, String> ROLE_LABELS = Map.of(
            "script", "Script creation",
            "video", "Video creation",
            "edit", "Video editing",
            "automation", "Automation");

    private static final Map<String, List<Map<String, Object>>> OPEN_SOURCE = Map.of(
            "script", List.of(
                    tool("ThiefMD", "kmwallio/ThiefMD", "https://github.com/kmwallio/ThiefMD",
                            "GPL-3.0", 184,
                            "Markdown / Fountain editor for drafting the spoken script and scene beats before you record.",
                            "Hook + build drafts",
                            "Keeps Fountain beats in one file so you do not bounce between Notes and Docs."),
                    tool("Fountain Editor", "chuangcaleb/obsidian-fountain-editor",
                            "https://github.com/chuangcaleb/obsidian-fountain-editor",
                            "MIT", 89,
                            "Obsidian plugin for screenplay-style scripts when you already write notes there.",
                            "Close / CTA lines",
                            "Drops the close and CTA next to your research notes in the same vault.")),
            "video", List.of(
                    tool("OBS Studio", "obsproject/obs-studio", "https://github.com/obsproject/obs-studio",
                            "GPL-2.0", 67000,
                            "Record camera, screen, or both in one take for Shorts, Reels, or talking-head clips.",
                            "A-roll capture",
                            "One-click scenes and recording presets so you start filming without a studio setup."),
                    tool("ShareX", "ShareX/ShareX", "https://github.com/ShareX/ShareX",
                            "GPL-3.0", 34000,
                            "Fast screen capture and short clip recording when you only need a B-roll insert.",
                            "B-roll inserts",
                            "Grab a 5-second insert without opening a full editor.")),
            "edit", List.of(
                    tool("Shotcut", "mltframework/shotcut", "https://github.com/mltframework/shotcut",
                            "GPL-3.0", 15400,
                            "Desktop editor to cut A-roll, burn captions, and export Shorts / Reels / YouTube.",
                            "Rough cut + captions",
                            "Trim, title, and export vertical in one timeline without a subscription."),
                    tool("Kdenlive", "KDE/kdenlive", "https://github.com/KDE/kdenlive",
                            "GPL-3.0", 5834,
                            "Full timeline editor when the cut needs titles, layers, or more than a quick trim.",
                            "Titles + layers",
                            "Multi-track titles and effects when CapCut templates are not enough.")),
            "automation", List.of(
                    tool("Huginn", "huginn/huginn", "https://github.com/huginn/huginn",
                            "MIT", 50041,
                            "Self-hosted agents that watch the story and trigger or schedule the publish step.",
                            "Publish trigger",
                            "Watches the story and queues the post so you do not babysit the clock.")));

    private static final Map<String, List<Map<String, String>>> PAID = Map.of(
            "script", List.of(
                    paid("ChatGPT", "https://chatgpt.com",
                            "Draft the hook, three beats, on-screen lines, and caption from the news angle.",
                            "Free ChatGPT can brainstorm short lines. Listed as paid because Plus/Team unlock GPT-4o, higher rate limits, projects, and longer context for a full script + caption pass in one go.",
                            "Hook + full draft",
                            "One prompt pass turns the news angle into hook, beats, and caption."),
                    paid("Jasper", "https://www.jasper.ai",
                            "Brand-voice copy desk for post bodies, titles, and CTA variants.",
                            "No durable free writing workspace; the App / Business plans are what unlock templates, brand voice, and team drafts.",
                            "Title + CTA variants",
                            "Brand-voice templates spit out title and CTA options without a blank page.")),
            "video", List.of(
                    paid("Runway", "https://runwayml.com",
                            "Generative video when you need a B-roll shot you cannot film today.",
                            "Credits and Gen models sit behind paid plans; the free allowance is too small for a finished social clip.",
                            "Generated B-roll",
                            "Fills missing shots in minutes when you cannot film on location."),
                    paid("Riverside", "https://riverside.fm",
                            "Remote or studio-quality camera and mic recording in the browser.",
                            "Local high-res recording and separate tracks sit behind paid plans; the free tier is capped.",
                            "Talking-head take",
                            "Records camera and mic as separate tracks so the edit starts clean.")),
            "edit", List.of(
                    paid("CapCut", "https://www.capcut.com",
                            "Templates, auto-captions, and vertical export for Shorts and Reels.",
                            "Free CapCut covers basic cuts. Listed as paid for Pro templates, cloud projects, and watermark-free pro effects used on polished Shorts.",
                            "Auto-captions + export",
                            "Templates and auto-captions cut vertical polish time to under an hour."),
                    paid("Adobe Premiere", "https://www.adobe.com/products/premiere.html",
                            "Pro timeline editor for multi-track cuts, color, and broadcast-style export.",
                            "Premiere is Creative Cloud subscription software; there is no free full editor for this workflow.",
                            "Pro color + multi-track",
                            "Handles multi-track color and export when the cut must look broadcast-ready.")),
            "automation", List.of(
                    paid("Buffer", "https://buffer.com",
                            "Queue the YouTube, Instagram, and LinkedIn posts for the same day.",
                            "Free Buffer is one channel and a tiny queue. Paid plans unlock multi-channel scheduling used in this plan.",
                            "Same-day queue",
                            "One calendar pushes YouTube, Instagram, and LinkedIn without re-uploading."),
                    paid("Later", "https://later.com",
                            "Visual calendar for Reels, Stories, and feed posts.",
                            "Free Later is capped; paid unlocks the visual planner, Linkin.bio, and multi-profile queues.",
                            "Visual schedule",
                            "Drag-and-drop Reels and Stories so the day is planned at a glance."),
                    paid("Hootsuite", "https://www.hootsuite.com",
                            "Shared queue when more than one account posts in the same hour.",
                            "Team inboxes, approvals, and multi-account queues are paid; the free trial is not a lasting solo plan.",
                            "Team approvals",
                            "Approvals and multi-account queues keep collaborators off your login.")));

    private SocialMediaPlan() {
    }

    public static boolean isSocial(BusinessIdea idea) {
        return idea != null && "comparison_content".equals(idea.angleType());
    }

    /** False when a cached social plan still lacks tool URLs, use summaries, or the edit role. */
    public static boolean hasLinkedTools(Map<?, ?> plan) {
        if (plan == null || !(plan.get("platforms") instanceof List<?> platforms) || platforms.isEmpty()) {
            return false;
        }
        Object first = platforms.get(0);
        if (!(first instanceof Map<?, ?> platform) || !(platform.get("hours") instanceof List<?> hours) || hours.isEmpty()) {
            return false;
        }
        if (hours.size() < 4) {
            return false;
        }
        boolean sawEdit = false;
        boolean sawScriptDetail = false;
        for (Object hourObj : hours) {
            if (!(hourObj instanceof Map<?, ?> hour)) {
                return false;
            }
            String role = String.valueOf(hour.get("role"));
            if ("edit".equals(role) || "Video editing".equals(String.valueOf(hour.get("roleLabel")))) {
                sawEdit = true;
            }
            if ("script".equals(role)) {
                sawScriptDetail = hasScriptDetail(hour);
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
            if (blank(row.get("summary")) || blank(row.get("whyPaid"))) {
                return false;
            }
        }
        return sawEdit && sawScriptDetail;
    }

    private static boolean hasScriptDetail(Map<?, ?> hour) {
        if (!(hour.get("detail") instanceof Map<?, ?> detail)) {
            return false;
        }
        if (blank(detail.get("title"))
                || blank(detail.get("idealLength"))
                || blank(detail.get("demoMaterial"))
                || blank(detail.get("howToCreateDemo"))) {
            return false;
        }
        if (!(detail.get("phases") instanceof List<?> phases) || phases.size() < 3) {
            return false;
        }
        Object first = phases.get(0);
        return first instanceof Map<?, ?> phase
                && !blank(phase.get("name"))
                && !blank(phase.get("goal"))
                && !blank(phase.get("tool"))
                && !blank(phase.get("speedUp"));
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
                platform("youtube", "YouTube", youtube(subject), subject),
                platform("instagram", "Instagram", instagram(subject), subject),
                platform("linkedin", "LinkedIn", linkedin(subject), subject));
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

    private static Map<String, Object> platform(String id, String label, List<String> tasks, String subject) {
        List<Map<String, Object>> hours = new ArrayList<>();
        for (int i = 0; i < ROLES.size(); i++) {
            hours.add(hour(i + 1, ROLES.get(i), tasks.get(i), id, subject));
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("label", label);
        row.put("hours", hours);
        return row;
    }

    private static Map<String, Object> hour(int index, String role, String task, String platformId, String subject) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("hour", "Hour " + index);
        row.put("role", role);
        row.put("roleLabel", ROLE_LABELS.get(role));
        row.put("task", task);
        row.put("detail", detailFor(role, platformId, subject, task));
        row.put("openSource", OPEN_SOURCE.get(role));
        row.put("paid", PAID.get(role));
        return row;
    }

    private static Map<String, Object> detailFor(String role, String platformId, String subject, String task) {
        return switch (role) {
            case "script" -> scriptDetail(platformId, subject);
            case "video" -> Map.of(
                    "focus", "Capture the A-roll and any B-roll the script needs.",
                    "outcome", task);
            case "edit" -> Map.of(
                    "focus", "Cut, caption, and export the finished vertical or square clip.",
                    "outcome", task);
            case "automation" -> Map.of(
                    "focus", "Schedule and publish on the same day.",
                    "outcome", task);
            default -> Map.of("outcome", task);
        };
    }

    private static Map<String, Object> scriptDetail(String platformId, String subject) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("title", scriptTitle(platformId, subject));
        detail.put("idealLength", idealLength(platformId));
        detail.put("demoMaterial", demoMaterial(platformId, subject));
        detail.put("howToCreateDemo", howToCreateDemo(platformId, subject));
        detail.put("phases", scriptPhases(platformId, subject));
        return detail;
    }

    private static String scriptTitle(String platformId, String subject) {
        return switch (platformId) {
            case "instagram" -> "Demo Reel: " + subject;
            case "linkedin" -> "Walkthrough post: " + subject;
            default -> "Demo video: " + subject;
        };
    }

    private static String idealLength(String platformId) {
        return switch (platformId) {
            case "instagram" -> "60–90 seconds spoken (about 150–220 words), with a 20–30 second on-screen demo in the middle";
            case "linkedin" -> "2–3 minutes spoken (about 300–450 words), or a 6–8 frame carousel that includes the demo screens";
            default -> "6–10 minutes spoken (about 900–1,500 words): hook, context, live demo, and CTA";
        };
    }

    private static String demoMaterial(String platformId, String subject) {
        return switch (platformId) {
            case "instagram" -> "One tight screen recording or before/after slide that proves \""
                    + subject + "\" in under 30 seconds. Show the click path, not a slide deck.";
            case "linkedin" -> "A short loom-style walkthrough or 3–4 annotated screenshots that show the workflow behind \""
                    + subject + "\". Prefer real UI over stock graphics.";
            default -> "A full walkthrough demo for \"" + subject
                    + "\": screen capture of the product flow, one sample input, the result on screen, and a 10-second recap card.";
        };
    }

    private static String howToCreateDemo(String platformId, String subject) {
        return switch (platformId) {
            case "instagram" -> "1) Write the 3 on-screen lines first. 2) Record the click path with ShareX or OBS in 9:16. "
                    + "3) Keep only the 20–30 seconds that prove \"" + subject + "\". 4) Drop that clip into CapCut between hook and CTA.";
            case "linkedin" -> "1) Script the talking points. 2) Capture the workflow with OBS or annotated screenshots. "
                    + "3) Export 6–8 frames (problem → steps → result). 4) Record a 2–3 minute voiceover over those frames.";
            default -> "1) Outline hook → problem → steps → result in ThiefMD. 2) Capture the full flow with OBS (clean data, large UI). "
                    + "3) Rehearse once, then record A-roll + screen in one take or as separate tracks. "
                    + "4) Edit in Shotcut/Kdenlive: burn step numbers, cut dead air, end on the result + CTA for \"" + subject + "\".";
        };
    }

    private static List<Map<String, String>> scriptPhases(String platformId, String subject) {
        String openTool = "ChatGPT · ThiefMD";
        String buildTool = "ThiefMD · Fountain Editor";
        String closeTool = "Jasper · Fountain Editor";
        return List.of(
                phase(
                        "Cold open",
                        "Name the news in one line and why it matters for \"" + subject + "\". Promise the demo viewers will see.",
                        openTool,
                        "ChatGPT drafts the hook; ThiefMD locks the spoken first line so you do not rewrite on camera."),
                phase(
                        "The build + demo",
                        platformId.equals("instagram")
                                ? "Walk the three beats, then cut to the on-screen demo that proves the angle."
                                : "Context, who it hits, then the live demo: sample input → clicks → result for \"" + subject + "\".",
                        buildTool,
                        "Fountain beats keep the middle paced so the demo lands before viewers drop off."),
                phase(
                        "The payoff",
                        platformId.equals("linkedin")
                                ? "Recap the result, CTA, and source link for the first comment."
                                : "Recap what the demo proved, CTA, and where to try it next.",
                        closeTool,
                        "Jasper spins CTA variants; Fountain Editor parks the close next to your research notes."));
    }

    private static Map<String, String> phase(String name, String goal, String tool, String speedUp) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("goal", goal);
        row.put("tool", tool);
        row.put("speedUp", speedUp);
        return Map.copyOf(row);
    }

    private static List<String> youtube(String subject) {
        return List.of(
                "Write a 6–10 minute YouTube script for \"" + subject + "\" with a live demo in the middle.",
                "Record A-roll plus the screen demo for that walkthrough.",
                "Edit the cut, burn step captions, and export the long-form demo video.",
                "Upload it, write the title and description, and schedule it for today or tomorrow.");
    }

    private static List<String> instagram(String subject) {
        return List.of(
                "Write a 60–90 second Reel script for \"" + subject + "\" with a 20–30 second demo insert.",
                "Film the talking take and capture the demo clip in 9:16.",
                "Edit the Reel: cut, captions, demo insert, music bed, and vertical export.",
                "Schedule the Reel and one story that points to it. Do both the same day.");
    }

    private static List<String> linkedin(String subject) {
        return List.of(
                "Write a 2–3 minute LinkedIn walkthrough script for \"" + subject + "\" (or a 6–8 frame demo carousel).",
                "Record the talking-head take and capture demo screens or stills.",
                "Edit the clip or assemble the carousel frames from the script.",
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
            String name,
            String repo,
            String url,
            String licence,
            int stars,
            String summary,
            String scriptPart,
            String speedUp) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("repo", repo);
        row.put("url", url);
        row.put("licence", licence);
        row.put("stars", stars);
        row.put("summary", summary);
        row.put("scriptPart", scriptPart);
        row.put("speedUp", speedUp);
        return Map.copyOf(row);
    }

    private static Map<String, String> paid(
            String name, String url, String summary, String whyPaid, String scriptPart, String speedUp) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("url", url);
        row.put("summary", summary);
        row.put("whyPaid", whyPaid);
        row.put("scriptPart", scriptPart);
        row.put("speedUp", speedUp);
        return Map.copyOf(row);
    }
}
