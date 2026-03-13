package com.demo.app;

import com.demo.app.quota.QuotaActionBody;
import com.demo.app.quota.QuotaReserveBody;
import com.myorg.lsf.quota.api.QuotaReservationFacade;
import com.myorg.lsf.quota.api.QuotaResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/quota")
@RequiredArgsConstructor
public class DemoQuotaController {

    private final QuotaReservationFacade quota;

    @PostMapping("/reserve")
    public QuotaResult reserve(
            @RequestParam(name = "key") String key,
            @RequestParam(name = "amount", defaultValue = "1") int amount,
            @RequestParam(name = "requestId", required = false) String requestId
    ) {
        return quota.reserve(key, normalizeRequestId(requestId), amount);
    }

    @PostMapping("/reserve-json")
    public QuotaResult reserveJson(@RequestBody QuotaReserveBody body) {
        int amount = body.amount() == null ? 1 : body.amount();
        return quota.reserve(body.key(), normalizeRequestId(body.requestId()), amount);
    }

    @PostMapping("/confirm")
    public QuotaResult confirm(
            @RequestParam(name = "key") String key,
            @RequestParam(name = "requestId") String requestId
    ) {
        return quota.confirm(key, requestId);
    }

    @PostMapping("/confirm-json")
    public QuotaResult confirmJson(@RequestBody QuotaActionBody body) {
        return quota.confirm(body.key(), body.requestId());
    }

    @PostMapping("/release")
    public QuotaResult release(
            @RequestParam(name = "key") String key,
            @RequestParam(name = "requestId") String requestId
    ) {
        return quota.release(key, requestId);
    }

    @PostMapping("/release-json")
    public QuotaResult releaseJson(@RequestBody QuotaActionBody body) {
        return quota.release(body.key(), body.requestId());
    }

    private String normalizeRequestId(String requestId) {
        return (requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId;
    }
}