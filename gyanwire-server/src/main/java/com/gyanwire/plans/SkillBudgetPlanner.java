package com.gyanwire.plans;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SkillBudgetPlanner {

    public record Item(
            String id,
            String name,
            int costInr,
            String billing,
            String bucket,
            String freeAlternativeId,
            int weeksSaved,
            int priority,
            boolean free
    ) {
    }

    public record Line(String id, String name, String bucket, int costInr, String billing, LocalDate cancelBy, String source) {
    }

    public record Plan(int skillBudgetMonth, List<Line> lines, int total, int monthsToGoal) {
    }

    public record WeekStep(
            int weekNo,
            String goal,
            List<String> tasks,
            String metric,
            String toolId,
            String toolName,
            int costInr,
            String billing,
            LocalDate cancelBy
    ) {
    }

    public record Path(
            Plan budget,
            String goal,
            String ideaTitle,
            String newsTitle,
            String newsUrl,
            String offer,
            List<WeekStep> weeks
    ) {
    }

    private SkillBudgetPlanner() {
    }

    public static int incomeProxy(String band) {
        if (band == null) {
            return 0;
        }
        return switch (band) {
            case "0" -> 0;
            case "under_15k" -> 7500;
            case "15_30" -> 22500;
            case "30_50" -> 40000;
            case "50_100" -> 75000;
            case "over_100" -> 100000;
            default -> 0;
        };
    }

    public static int budget(String band, int investPct) {
        return budgetFromProxy(incomeProxy(band), investPct);
    }

    public static int budgetFromProxy(int proxy, int investPct) {
        int pct = Math.max(5, Math.min(15, investPct));
        if (proxy <= 0) {
            return 0;
        }
        return proxy * pct / 100;
    }

    public static int[] split(String goal) {
        return switch (goal == null ? "" : goal) {
            case "switch_job" -> new int[]{50, 20, 20, 10};
            case "start_business" -> new int[]{25, 35, 30, 10};
            case "side_income" -> new int[]{35, 30, 25, 10};
            default -> new int[]{40, 30, 20, 10};
        };
    }

    public static Plan plan(String goal, int monthBudget, List<Item> catalog, LocalDate today) {
        int budget = Math.max(0, monthBudget);
        List<Item> pool = new ArrayList<>(catalog == null ? List.of() : catalog);
        pool.sort(Comparator.comparing(Item::free).reversed()
                .thenComparing(Item::priority, Comparator.reverseOrder())
                .thenComparingInt(Item::costInr));
        List<Line> lines = new ArrayList<>();
        int spent = 0;
        int paid = 0;
        for (Item item : pool) {
            if (!eligible(item, budget)) {
                continue;
            }
            if (spent + item.costInr() > budget) {
                continue;
            }
            lines.add(line(item, today));
            spent += item.costInr();
            if (!item.free()) {
                paid += item.costInr();
            }
        }
        int months = budget == 0 ? 0 : (int) Math.ceil(paid / (double) budget);
        return new Plan(budget, lines, spent, months);
    }

    /**
     * A short path for one news idea: at most one learning tool, one make tool,
     * one proof upgrade, and one community tool. Paid items replace a free one
     * only when they match the idea, save at least four weeks, and fit the month.
     */
    public static Path path(
            String ideaTitle,
            String newsTitle,
            String ideaText,
            String goal90d,
            int monthBudget,
            int hoursPerWeek,
            boolean lighter,
            List<Item> catalog,
            Map<String, List<String>> skillsById,
            Set<String> knownSkills,
            LocalDate today
    ) {
        int budget = Math.max(0, monthBudget);
        String idea = blank(ideaTitle) ? "this idea" : ideaTitle.trim();
        String news = blank(newsTitle) ? "this finding" : newsTitle.trim();
        String text = (ideaText == null ? "" : ideaText).toLowerCase(Locale.ROOT);
        Map<String, List<String>> skills = skillsById == null ? Map.of() : skillsById;
        Set<String> known = knownSkills == null ? Set.of() : knownSkills;
        List<Item> catalogItems = catalog == null ? List.of() : catalog;

        List<Item> picked = new ArrayList<>();
        int spent = 0;
        boolean paidUsed = false;
        for (String bucket : List.of("learning", "tools", "proof", "community")) {
            List<Item> ranked = rank(bucket, text, budget, catalogItems, skills, known);
            Item free = null;
            Item paid = null;
            for (Item item : ranked) {
                if (item.free() && free == null) {
                    free = item;
                }
                if (!item.free() && paid == null && score(item, text, skills) > 0) {
                    paid = item;
                }
            }
            Item chosen = free;
            if (paid != null && !paidUsed && spent + paid.costInr() <= budget && score(paid, text, skills) >= score(free, text, skills)) {
                chosen = paid;
            }
            if (chosen == null) {
                continue;
            }
            if ("proof".equals(bucket) && score(chosen, text, skills) == 0) {
                continue;
            }
            if (spent + chosen.costInr() > budget) {
                continue;
            }
            if (!chosen.free()) {
                paidUsed = true;
            }
            picked.add(chosen);
            spent += chosen.costInr();
        }

        List<Line> lines = new ArrayList<>();
        int paid = 0;
        for (Item item : picked) {
            lines.add(line(item, today));
            if (!item.free()) {
                paid += item.costInr();
            }
        }
        int months = budget == 0 ? 0 : (int) Math.ceil(paid / (double) budget);
        Plan plan = new Plan(budget, lines, spent, months);
        List<WeekStep> weeks = weeks(idea, news, goal90d, hoursPerWeek, lighter, picked, today);
        String goal = "Finish " + idea + " from the news “" + news + "”";
        return new Path(plan, goal, idea, news, "", "", weeks);
    }

    private static List<WeekStep> weeks(
            String idea,
            String news,
            String goal90d,
            int hoursPerWeek,
            boolean lighter,
            List<Item> picked,
            LocalDate today
    ) {
        int total = WeeklyPlanner.horizon(hoursPerWeek);
        int taskCount = lighter ? 2 : 3;
        int sittings = Math.max(1, hoursPerWeek / 5);
        int proveFrom = Math.max(2, total / 2);
        Item learning = byBucket(picked, "learning");
        Item tool = byBucket(picked, "tools");
        Item proof = byBucket(picked, "proof");
        Item community = byBucket(picked, "community");
        List<WeekStep> weeks = new ArrayList<>();
        for (int week = 1; week <= total; week++) {
            String phase = phase(week, total, proveFrom, proof != null);
            Item use = toolFor(phase, learning, tool, proof, community);
            if (use == null) {
                use = tool != null ? tool : learning;
            }
            String name = use == null ? "a notebook" : use.name();
            String goal = weekGoal(phase, week, idea, news, name, goal90d);
            String metric = metric(phase);
            List<String> tasks = new ArrayList<>();
            tasks.add("Use " + name + " for this goal");
            tasks.add(taskFor(phase, idea, news, sittings));
            if (!lighter) {
                tasks.add(week == total ? "Write continue or stop, and the reason" : "Save one artifact you can show someone");
            }
            LocalDate cancelBy = use != null && "monthly".equals(use.billing()) ? today.plusDays(30) : null;
            weeks.add(new WeekStep(
                    week,
                    goal,
                    List.copyOf(tasks.subList(0, Math.min(taskCount, tasks.size()))),
                    metric,
                    use == null ? "notes" : use.id(),
                    name,
                    use == null ? 0 : use.costInr(),
                    use == null ? "free" : use.billing(),
                    cancelBy
            ));
        }
        return weeks;
    }

    private static String phase(int week, int total, int proveFrom, boolean hasProof) {
        if (week == 1) {
            return "learn";
        }
        if (week == total) {
            return "decide";
        }
        if (hasProof && week >= proveFrom) {
            return "prove";
        }
        return "build";
    }

    private static Item toolFor(String phase, Item learning, Item tool, Item proof, Item community) {
        return switch (phase) {
            case "learn" -> learning != null ? learning : tool;
            case "prove" -> proof != null ? proof : tool;
            case "decide" -> community != null ? community : tool;
            default -> tool != null ? tool : learning;
        };
    }

    private static String weekGoal(String phase, int week, String idea, String news, String tool, String goal90d) {
        return switch (phase) {
            case "learn" -> "Write the offer for " + idea + " from “" + news + "”";
            case "prove" -> "Show " + idea + " to one person";
            case "decide" -> decideGoal(idea, goal90d);
            default -> week % 2 == 0
                    ? "Practice " + idea + " in " + tool
                    : "Ship a small piece of " + idea;
        };
    }

    private static String decideGoal(String idea, String goal90d) {
        return switch (goal90d == null ? "" : goal90d) {
            case "switch_job" -> "Decide if " + idea + " is a piece you can show for a job";
            case "start_business", "side_income", "first_income" -> "Decide if someone would pay for " + idea;
            default -> "Decide what to practice next on " + idea;
        };
    }

    private static String taskFor(String phase, String idea, String news, int sittings) {
        return switch (phase) {
            case "learn" -> "Read “" + news + "” and write who pays for " + idea + " in one sentence";
            case "prove" -> "Ask one person if they would use " + idea;
            case "decide" -> "Compare what you shipped with the offer you wrote in week 1";
            default -> "Spend " + sittings + " focused sittings on " + idea;
        };
    }

    private static String metric(String phase) {
        return switch (phase) {
            case "learn" -> "Offer written in one sentence";
            case "prove" -> "One person has seen it";
            case "decide" -> "Continue or stop is written down";
            default -> "One artifact saved";
        };
    }

    private static List<Item> rank(
            String bucket,
            String text,
            int budget,
            List<Item> catalog,
            Map<String, List<String>> skills,
            Set<String> known
    ) {
        List<Item> items = new ArrayList<>();
        for (Item item : catalog) {
            if (!bucket.equals(item.bucket()) || !eligible(item, budget) || mastered(skillList(item, skills), known)) {
                continue;
            }
            items.add(item);
        }
        items.sort((a, b) -> {
            int byScore = Integer.compare(score(b, text, skills), score(a, text, skills));
            if (byScore != 0) {
                return byScore;
            }
            if (a.free() != b.free()) {
                return a.free() ? -1 : 1;
            }
            if (score(a, text, skills) == 0) {
                int byGeneric = Integer.compare(genericRank(skillList(b, skills)), genericRank(skillList(a, skills)));
                if (byGeneric != 0) {
                    return byGeneric;
                }
            }
            int byPriority = Integer.compare(b.priority(), a.priority());
            if (byPriority != 0) {
                return byPriority;
            }
            return Integer.compare(a.costInr(), b.costInr());
        });
        return items;
    }

    private static int score(Item item, String text, Map<String, List<String>> skills) {
        if (item == null) {
            return 0;
        }
        int score = 0;
        for (String skill : skillList(item, skills)) {
            if (skill.isBlank()) {
                continue;
            }
            String key = skill.toLowerCase(Locale.ROOT);
            if (text.contains(key)) {
                score += 5;
            }
            for (String alias : aliases(key)) {
                if (text.contains(alias)) {
                    score += 3;
                }
            }
        }
        for (String token : item.name().toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() > 3 && text.contains(token)) {
                score += 2;
            }
        }
        return score;
    }

    private static List<String> aliases(String skill) {
        return switch (skill) {
            case "python", "code", "ide" -> List.of("python", "code", "app", "software", "program");
            case "design" -> List.of("design", "poster", "brand", "visual");
            case "sales" -> List.of("sales", "sell", "customer", "client");
            case "writing" -> List.of("write", "writing", "newsletter", "content");
            case "site" -> List.of("website", "site", "blog", "landing");
            case "payments" -> List.of("payment", "razorpay", "checkout", "invoice");
            case "analytics" -> List.of("analytics", "traffic", "metric");
            case "sheets", "ops" -> List.of("spreadsheet", "sheet", "tracker");
            case "notes" -> List.of("notes", "notion");
            case "network" -> List.of("meetup", "founder", "community");
            default -> List.of();
        };
    }

    private static int genericRank(List<String> skills) {
        for (String skill : skills) {
            String key = skill.toLowerCase(Locale.ROOT);
            if (List.of("notes", "writing", "sheets", "ops", "learning").contains(key)) {
                return 2;
            }
        }
        return 0;
    }

    private static boolean mastered(List<String> skills, Set<String> known) {
        if (skills.isEmpty() || known.isEmpty()) {
            return false;
        }
        for (String skill : skills) {
            if (!known.contains(skill.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> skillList(Item item, Map<String, List<String>> skills) {
        List<String> values = skills.get(item.id());
        return values == null ? List.of() : values;
    }

    private static Item byBucket(List<Item> items, String bucket) {
        for (Item item : items) {
            if (bucket.equals(item.bucket())) {
                return item;
            }
        }
        return null;
    }

    private static boolean eligible(Item item, int budget) {
        if (item.free()) {
            return true;
        }
        return budget > 0 && item.weeksSaved() >= 4 && item.costInr() <= budget;
    }

    private static Line line(Item item, LocalDate today) {
        LocalDate cancelBy = "monthly".equals(item.billing()) ? today.plusDays(30) : null;
        return new Line(item.id(), item.name(), item.bucket(), item.costInr(), item.billing(), cancelBy, "catalog");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
