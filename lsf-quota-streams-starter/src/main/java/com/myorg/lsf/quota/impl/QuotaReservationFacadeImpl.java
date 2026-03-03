package com.myorg.lsf.quota.impl;

import com.myorg.lsf.quota.api.*;
import com.myorg.lsf.quota.policy.QuotaPolicy;
import com.myorg.lsf.quota.policy.QuotaPolicyProvider;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class QuotaReservationFacadeImpl implements QuotaReservationFacade {

    private final QuotaService quotaService; // backend (redis/memory)
    private final QuotaPolicyProvider policyProvider;

    @Override
    public QuotaResult reserve(String quotaKey, String requestId, int amount) {
        QuotaPolicy policy = policyProvider.findPolicy(quotaKey)
                .orElseThrow(() -> new IllegalArgumentException("No quota policy for key=" + quotaKey));
        System.out.println("reserve key=" + quotaKey);
        System.out.println("available keys=" + policyProvider.findPolicy("dummy").isPresent());
        return quotaService.reserve(QuotaRequest.builder()
                .quotaKey(quotaKey)
                .requestId(requestId)
                .amount(amount)
                .limit(policy.limit())
                .hold(policy.hold())
                .build());
    }

    @Override
    public QuotaResult confirm(String quotaKey, String requestId) {
        return quotaService.confirm(quotaKey, requestId);
    }

    @Override
    public QuotaResult release(String quotaKey, String requestId) {
        return quotaService.release(quotaKey, requestId);
    }
}