package com.maxlapushkin.delivery.controller;

import com.maxlapushkin.delivery.dto.DeliveryStateChangeResponse;
import com.maxlapushkin.delivery.DeliveryCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryCommandController {

    private final DeliveryCommandService deliveryCommandService;

    @PostMapping("/{id}/mark-in-transit")
    public DeliveryStateChangeResponse markInTransit(@PathVariable Long id) {
        return deliveryCommandService.markInTransit(id);
    }

    @PostMapping("/{id}/deliver")
    public DeliveryStateChangeResponse deliver(@PathVariable Long id) {
        return deliveryCommandService.deliver(id);
    }

    @PostMapping("/{id}/return")
    public DeliveryStateChangeResponse returnDelivery(@PathVariable Long id) {
        return deliveryCommandService.returnDelivery(id);
    }

    @PostMapping("/{id}/cancel")
    public DeliveryStateChangeResponse cancel(@PathVariable Long id) {
        return deliveryCommandService.cancel(id);
    }
}