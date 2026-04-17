package com.maxlapushkin.delivery.dto;

import java.time.Instant;

public record DeliveryTimelineResponse(
        Long id,
        Long deliveryId,
        Long orderId,
        String eventId,
        String eventType,
        String status,
        Instant occurredAt,
        String payloadJson
) {
}
