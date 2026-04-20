package com.maxlapushkin.delivery.controller;

import com.maxlapushkin.delivery.dto.DeliveryResponse;
import com.maxlapushkin.delivery.dto.DeliveryTimelineResponse;
import com.maxlapushkin.delivery.DeliveryQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryQueryController {

    private final DeliveryQueryService deliveryQueryService;

    @GetMapping("/{id}")
    public DeliveryResponse findById(@PathVariable Long id) {
        return deliveryQueryService.findById(id);
    }

    @GetMapping("/by-order/{orderId}")
    public DeliveryResponse findByOrderId(@PathVariable Long orderId) {
        return deliveryQueryService.findByOrderId(orderId);
    }

    @GetMapping("/{id}/timeline")
    public List<DeliveryTimelineResponse> getTimeline(@PathVariable Long id) {
        return deliveryQueryService.getTimeline(id);
    }
}
