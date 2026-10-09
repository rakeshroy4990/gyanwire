package com.gyanwire.research.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RobotsTxt {

    private final List<Rule> rules;

    private RobotsTxt(List<Rule> rules) {
        this.rules = rules;
    }

    public static RobotsTxt parse(String body) {
        List<Rule> rules = new ArrayList<>();
        List<String> agents = new ArrayList<>();
        boolean capturing = false;
        if (body != null) {
            for (String raw : body.split("\\R")) {
                String line = raw;
                int comment = line.indexOf('#');
                if (comment >= 0) {
                    line = line.substring(0, comment);
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();
                if (key.equals("user-agent")) {
                    if (!capturing) {
                        agents = new ArrayList<>();
                        capturing = true;
                    }
                    agents.add(value.toLowerCase(Locale.ROOT));
                } else if (key.equals("allow") || key.equals("disallow")) {
                    capturing = false;
                    if (agents.isEmpty() || !applies(agents)) {
                        continue;
                    }
                    rules.add(new Rule(key.equals("allow"), value));
                }
            }
        }
        return new RobotsTxt(rules);
    }

    public boolean allows(String path) {
        String target = path == null || path.isBlank() ? "/" : path;
        Rule best = null;
        for (Rule rule : rules) {
            if (rule.path.isEmpty()) {
                continue;
            }
            if (target.startsWith(rule.path) && (best == null || rule.path.length() > best.path.length())) {
                best = rule;
            }
        }
        return best == null || best.allow;
    }

    private static boolean applies(List<String> agents) {
        for (String agent : agents) {
            if (agent.equals("*") || agent.contains("gyanwire")) {
                return true;
            }
        }
        return false;
    }

    private record Rule(boolean allow, String path) {
    }
}
