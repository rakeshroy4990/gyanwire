package com.gyanwire.plans;

import com.gyanwire.llm.LlmRequest;
import com.gyanwire.llm.LlmResult;
import com.gyanwire.llm.LlmService;
import com.gyanwire.llm.ModelTier;
import com.gyanwire.llm.WhatIfNumbers;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class WhatIfNarrator {

    private final LlmService llm;

    public WhatIfNarrator(LlmService llm) {
        this.llm = llm;
    }

    public String explain(UUID userId, int costDelta, int hoursDelta) {
        String template = template(costDelta, hoursDelta);
        if (!llm.stageEnabled("whatif")) {
            return template;
        }
        String system = "Return JSON only: {\"text\":\"one paragraph\"}. Use only the numbers in the user message. Do not invent figures.";
        String input = "costDelta " + costDelta + "\nhoursDelta " + hoursDelta;
        LlmResult result = llm.call(new LlmRequest("whatif", ModelTier.LIGHT, system, input, userId, null));
        String text = result.json() == null ? "" : result.json().path("text").asText("");
        Set<String> allowed = WhatIfNumbers.allowed(costDelta, hoursDelta, Math.abs(costDelta), Math.abs(hoursDelta));
        if (!result.ok() || !WhatIfNumbers.onlyKnownNumbers(text, allowed)) {
            return template;
        }
        return text;
    }

    public static String template(int costDelta, int hoursDelta) {
        return "Estimate. Cost changes by " + costDelta + " rupees and hours by " + hoursDelta + ".";
    }
}
