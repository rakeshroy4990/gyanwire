package com.gyanwire.usage;

import com.gyanwire.persistence.FlowStore;
import com.gyanwire.persistence.postgres.model.PlanEntity;
import com.gyanwire.persistence.postgres.model.UserEntity;
import com.gyanwire.persistence.postgres.repository.PlanRepository;
import com.gyanwire.persistence.postgres.repository.SubscriptionRepository;
import com.gyanwire.persistence.postgres.repository.UsageEventRepository;
import com.gyanwire.persistence.postgres.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageServiceTest {

    @Mock PlanRepository planRepository;
    @Mock SubscriptionRepository subscriptionRepository;
    @Mock UsageEventRepository usageEventRepository;
    @Mock UserRepository userRepository;
    @Mock FlowStore flowStore;

    @InjectMocks UsageService usageService;

    @Test
    void evaluateSearchLimitBlocksWhenUsedReachesLimit() {
        Map<String, Object> evaluation = UsageService.evaluateSearchLimit(5, 5);
        assertThat(evaluation.get("allowed")).isEqualTo(false);
        assertThat(evaluation.get("remaining")).isEqualTo(0L);
    }

    @Test
    void evaluateSearchLimitAllowsWhenUnderLimit() {
        Map<String, Object> evaluation = UsageService.evaluateSearchLimit(2, 5);
        assertThat(evaluation.get("allowed")).isEqualTo(true);
        assertThat(evaluation.get("remaining")).isEqualTo(3L);
    }

    @Test
    void resolveUserPlanGivesUnlimitedIdeasForAdmin() {
        UUID userId = UUID.randomUUID();
        PlanEntity free = org.mockito.Mockito.mock(PlanEntity.class);
        when(free.getId()).thenReturn("free");
        when(free.getName()).thenReturn("Free");
        when(free.getDailySearchLimit()).thenReturn(5);
        when(free.getDailyIdeaLimit()).thenReturn(3);
        when(free.getSeats()).thenReturn(1);
        when(free.getDailyBriefLimit()).thenReturn(1);
        UserEntity admin = new UserEntity();
        admin.setId(userId);
        admin.setRole("admin");

        when(subscriptionRepository.findActiveForUser(userId)).thenReturn(List.of());
        when(planRepository.findById("free")).thenReturn(Optional.of(free));
        when(flowStore.bonusSearches(userId)).thenReturn(0);
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(admin));

        Map<String, Object> plan = usageService.resolveUserPlan(userId);

        assertThat(plan.get("dailyIdeaLimit")).isEqualTo(UsageService.UNLIMITED);
        assertThat(plan.get("allowsHighModel")).isEqualTo(false);
    }

    @Test
    void proPlanAllowsTheHighModel() {
        UUID userId = UUID.randomUUID();
        PlanEntity pro = org.mockito.Mockito.mock(PlanEntity.class);
        when(pro.getId()).thenReturn("pro");
        when(pro.getName()).thenReturn("Pro");
        when(pro.getDailySearchLimit()).thenReturn(100);
        when(pro.isAllowsHighModel()).thenReturn(true);
        com.gyanwire.persistence.postgres.model.SubscriptionEntity subscription =
                org.mockito.Mockito.mock(com.gyanwire.persistence.postgres.model.SubscriptionEntity.class);
        when(subscription.getPlanId()).thenReturn("pro");
        when(subscriptionRepository.findActiveForUser(userId)).thenReturn(java.util.List.of(subscription));
        when(planRepository.findById("pro")).thenReturn(Optional.of(pro));
        when(flowStore.bonusSearches(userId)).thenReturn(0);
        when(userRepository.findActiveById(userId)).thenReturn(Optional.empty());

        assertThat(usageService.allowsHighModel(userId)).isTrue();
        assertThat(usageService.allowsHighModel(null)).isFalse();
    }
}
