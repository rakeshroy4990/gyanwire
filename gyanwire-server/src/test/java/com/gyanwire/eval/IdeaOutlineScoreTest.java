package com.gyanwire.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gyanwire.plans.OutlineDraft;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IdeaOutlineScoreTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void fullIdeaSetScoresAboveASingleCourse() throws Exception {
        double full = IdeaOutlineScore.ideas(mapper.readTree(eightIdeas()));
        double thin = IdeaOutlineScore.ideas(mapper.readTree("""
                {"ideas":[{"title":"Online course on UPI fees","segment":"merchants","pain":"fees","offer":"course","business_model":"fee","why_now":"subsidy","first_customer_path":"shops","capital_needed_inr":0,"hours_per_week":4,"market_size":0.4,"competition_gap":0.4,"regulatory_risk":0.2,"confidence":0.5}]}
                """));
        assertThat(full).isGreaterThan(thin);
        assertThat(full).isEqualTo(1.0);
    }

    @Test
    void outlineKeepsRupeesAndRewardsARewrite() throws Exception {
        Map<String, Object> template = OutlineDraft.template("UPI merchant checklist", 2400, 62);
        ObjectNode copy = mapper.valueToTree(template);
        double unchanged = IdeaOutlineScore.outline(copy, template);
        copy.put("problem", "Merchants under ₹2,000 still need a simple way to keep the zero-fee window. Estimate.");
        copy.put("monthlyCost", 9999);
        double changedNumber = IdeaOutlineScore.outline(copy, template);
        copy.put("monthlyCost", 2400);
        double rewritten = IdeaOutlineScore.outline(copy, template);
        assertThat(rewritten).isGreaterThan(unchanged);
        assertThat(rewritten).isGreaterThan(changedNumber);
    }

    @Test
    void flagshipWinsOnlyWhenBothTasksImprove() {
        ObjectNode idea = mapper.createObjectNode();
        idea.putObject("low").put("quality", 0.4).put("ok", true);
        idea.putObject("high").put("quality", 0.9).put("ok", true);
        ObjectNode outline = mapper.createObjectNode();
        outline.putObject("low").put("quality", 0.5).put("ok", true);
        outline.putObject("high").put("quality", 0.8).put("ok", true);
        assertThat(ModelBakeoff.useFlagship(idea, outline)).isTrue();

        outline.withObject("high").put("quality", 0.4);
        assertThat(ModelBakeoff.useFlagship(idea, outline)).isFalse();

        outline.withObject("high").put("quality", 0.8).put("ok", false);
        assertThat(ModelBakeoff.useFlagship(idea, outline)).isFalse();
    }

    @Test
    void tinyGainBelowTheFloorDoesNotSwitch() {
        ObjectNode idea = mapper.createObjectNode();
        idea.putObject("low").put("quality", 0.4).put("ok", true);
        idea.putObject("high").put("quality", 0.425).put("ok", true);
        ObjectNode outline = mapper.createObjectNode();
        outline.putObject("low").put("quality", 1.0).put("ok", true);
        outline.putObject("high").put("quality", 1.0).put("ok", true);
        assertThat(ModelBakeoff.useFlagship(idea, outline)).isFalse();
    }

    private static String eightIdeas() {
        return """
                {"ideas":[
                  {"title":"Online course on UPI fees","segment":"shop owners","pain":"unclear fees","offer":"a short online course","business_model":"Course fee","why_now":"zero-fee window continues until 31 March","first_customer_path":"merchant groups","capital_needed_inr":0,"hours_per_week":4,"market_size":0.5,"competition_gap":0.5,"regulatory_risk":0.2,"confidence":0.6},
                  {"title":"UPI fee checklist","segment":"shop owners","pain":"missed rules","offer":"a one-page checklist","business_model":"One-time download","why_now":"the subsidy now ends on a known date","first_customer_path":"shops","capital_needed_inr":0,"hours_per_week":3,"market_size":0.4,"competition_gap":0.5,"regulatory_risk":0.2,"confidence":0.6},
                  {"title":"UPI volume tracker","segment":"shop owners","pain":"no view of volume","offer":"a tracker tool","business_model":"Low monthly fee","why_now":"transactions under ₹2,000 stay free","first_customer_path":"accountants","capital_needed_inr":0,"hours_per_week":4,"market_size":0.4,"competition_gap":0.4,"regulatory_risk":0.2,"confidence":0.5},
                  {"title":"UPI subsidy briefing","segment":"shop owners","pain":"dense notices","offer":"an explainer briefing","business_model":"Low monthly fee","why_now":"the March date is now public","first_customer_path":"newsletters","capital_needed_inr":0,"hours_per_week":3,"market_size":0.4,"competition_gap":0.5,"regulatory_risk":0.2,"confidence":0.5},
                  {"title":"UPI notice template pack","segment":"shop owners","pain":"blank pages","offer":"a template pack","business_model":"One-time download","why_now":"merchants must tell staff before March","first_customer_path":"templates","capital_needed_inr":0,"hours_per_week":3,"market_size":0.4,"competition_gap":0.5,"regulatory_risk":0.2,"confidence":0.5},
                  {"title":"Live working session on UPI fees","segment":"shop owners","pain":"no practice","offer":"a live working session","business_model":"Seat fee","why_now":"the window is time-boxed","first_customer_path":"workshops","capital_needed_inr":500,"hours_per_week":4,"market_size":0.3,"competition_gap":0.6,"regulatory_risk":0.2,"confidence":0.5},
                  {"title":"Setup help for UPI signage","segment":"shop owners","pain":"setup","offer":"done-for-you setup help","business_model":"Project fee","why_now":"shops need the notice before the date","first_customer_path":"local visits","capital_needed_inr":1000,"hours_per_week":5,"market_size":0.3,"competition_gap":0.6,"regulatory_risk":0.3,"confidence":0.5},
                  {"title":"UPI rule change alerts","segment":"shop owners","pain":"missed changes","offer":"an alert when the rule changes","business_model":"Low monthly fee","why_now":"the subsidy date can move again","first_customer_path":"sms list","capital_needed_inr":0,"hours_per_week":2,"market_size":0.4,"competition_gap":0.5,"regulatory_risk":0.2,"confidence":0.5}
                ]}
                """;
    }
}
