package com.maxlapushkin.delivery.dto;
import java.time.Instant;
import java.util.UUID;

public record DeliveryLifecycleEventPayload(
        UUID eventId,
        Long deliveryId,
        Long orderId,
        Instant occurredAt,
        String eventType,
        String status
) {
}
