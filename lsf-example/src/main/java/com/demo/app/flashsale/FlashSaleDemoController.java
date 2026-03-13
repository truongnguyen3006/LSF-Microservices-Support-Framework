package com.demo.app.flashsale;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/demo/flash-sale/orders")
@RequiredArgsConstructor
public class FlashSaleDemoController {

    private final FlashSaleReservationService service;

    @PostMapping("/reserve")
    public FlashSaleOrderResponse reserve(@RequestBody FlashSaleReserveBody body) {
        return service.reserve(body);
    }

    @PostMapping("/{orderId}/confirm")
    public FlashSaleOrderResponse confirm(@PathVariable("orderId") String orderId) {
        return service.confirm(orderId);
    }

    @PostMapping("/{orderId}/release")
    public FlashSaleOrderResponse release(@PathVariable("orderId") String orderId) {
        return service.release(orderId);
    }

    @GetMapping("/{orderId}")
    public FlashSaleOrderResponse get(@PathVariable("orderId") String orderId) {
        return service.get(orderId);
    }
}
