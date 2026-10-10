package com.gyanwire.plans;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.persistence.FlowStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalibrationServiceTest {

    @Test
    void doesNotCalibrateBelowMinSamples() {
        FlowStore store = mock(FlowStore.class);
        when(store.optionTaskAggregates("vscode", "coding"))
                .thenReturn(List.of(Map.of("optionId", "vscode", "taskType", "coding", "n", 5, "avgHours", 4.0)));
        CalibrationService service = new CalibrationService(store, new ObjectMapper());
        assertThat(service.maybeCalibrate("vscode", "coding", 10.0)).isFalse();
        verify(store, never()).updateOptionTaskFit(anyString(), anyString(), anyInt());
    }

    @Test
    void calibratesWhenEnoughSamples() {
        FlowStore store = mock(FlowStore.class);
        when(store.optionTaskAggregates("paid-python", "learning"))
                .thenReturn(List.of(Map.of(
                        "optionId", "paid-python",
                        "taskType", "learning",
                        "n", CalibrationService.MIN_SAMPLES,
                        "avgHours", 5.0
                )));
        when(store.findCatalogItem("paid-python")).thenReturn(Map.of(
                "id", "paid-python",
                "taskFit", Map.of("learning", Map.of("speedup", List.of(1.1, 1.3), "confidence", "placeholder"))
        ));
        CalibrationService service = new CalibrationService(store, new ObjectMapper());
        assertThat(service.maybeCalibrate("paid-python", "learning", 10.0)).isTrue();
        verify(store).updateOptionTaskFit(eq("paid-python"), anyString(), eq(CalibrationService.MIN_SAMPLES));
    }
}
