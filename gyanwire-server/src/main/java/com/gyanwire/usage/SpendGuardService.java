package com.gyanwire.usage;

import com.gyanwire.persistence.postgres.repository.LlmCallRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class SpendGuardService implements SpendGuard {

    private final LlmCallRepository calls;
    private final UsageService usage;

    public SpendGuardService(LlmCallRepository calls, UsageService usage) {
        this.calls = calls;
        this.usage = usage;
    }

    @Override
    public boolean allow(UUID userId) {
        try {
            Instant start = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
            long in = userId == null ? calls.sumAnonTokensIn(start) : calls.sumUserTokensIn(userId, start);
            long out = userId == null ? calls.sumAnonTokensOut(start) : calls.sumUserTokensOut(userId, start);
            String planId = userId == null ? "anonymous" : String.valueOf(usage.resolveUserPlan(userId).get("id"));
            double spent = SpendCap.inr((int) Math.min(Integer.MAX_VALUE, in), (int) Math.min(Integer.MAX_VALUE, out), 10, 30);
            return SpendCap.allowed(spent, SpendCap.capFor(planId));
        } catch (Exception e) {
            return true;
        }
    }
}
