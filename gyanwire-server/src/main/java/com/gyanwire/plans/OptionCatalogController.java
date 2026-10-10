package com.gyanwire.plans;

import com.gyanwire.controller.dto.StandardApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class OptionCatalogController {

    private final OptionCatalogService options;

    public OptionCatalogController(OptionCatalogService options) {
        this.options = options;
    }

    @GetMapping("/api/options")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> list(
            @RequestParam(required = false) String taskType
    ) {
        List<Map<String, Object>> catalog = options.list(taskType);
        return ResponseEntity.ok(StandardApiResponse.success("OK", Map.of("options", catalog)));
    }
}
