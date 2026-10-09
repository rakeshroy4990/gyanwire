package com.gyanwire.projects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.config.PlanLimitException;
import com.gyanwire.persistence.FlowStore;
import com.gyanwire.usage.UsageService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProjectService {

    private final FlowStore store;
    private final UsageService usage;
    private final ObjectMapper mapper;

    public ProjectService(FlowStore store, UsageService usage, ObjectMapper mapper) {
        this.store = store;
        this.usage = usage;
        this.mapper = mapper;
    }

    public Map<String, Object> create(UUID userId, String name) {
        Map<String, Object> plan = usage.resolveUserPlan(userId);
        Integer cap = plan.get("projectLimit") instanceof Number n ? n.intValue() : null;
        if (cap != null && store.countProjects(userId) >= cap) {
            throw new PlanLimitException("Project limit reached.", "LIMIT_REACHED", Map.of("upgradeUrl", "/pricing"));
        }
        UUID id = store.insertProject(userId, name == null || name.isBlank() ? "Research project" : name.trim());
        return Map.of("id", id.toString(), "name", name);
    }

    public List<Map<String, Object>> list(UUID userId) {
        return store.listProjects(userId);
    }

    public void addItem(UUID userId, UUID projectId, String kind, Map<String, Object> ref) {
        try {
            store.insertProjectItem(projectId, kind, mapper.writeValueAsString(ref));
        } catch (Exception e) {
            throw new com.gyanwire.research.ResearchException("Could not save that item.", "VALIDATION_ERROR", 400);
        }
    }

    public String markdown(UUID userId, UUID projectId) {
        StringBuilder md = new StringBuilder("# Project\n\n");
        for (Map<String, Object> item : store.projectItems(projectId, userId)) {
            md.append("## ").append(item.get("kind")).append("\n\n");
            Object ref = item.get("ref");
            if (ref instanceof Map<?, ?> map) {
                Object title = map.get("title");
                Object url = map.get("url");
                Object why = map.get("why");
                if (title != null) {
                    md.append(title).append("\n\n");
                }
                if (why != null) {
                    md.append(why).append("\n\n");
                }
                if (url != null) {
                    md.append("Source: ").append(url).append("\n\n");
                }
            }
        }
        return md.toString();
    }

    public byte[] pdf(UUID userId, UUID projectId) {
        return SimplePdf.fromText(markdown(userId, projectId));
    }
}
