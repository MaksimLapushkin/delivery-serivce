package com.maxlapushkin.delivery.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderFulfilledEvent(
        UUID eventId,
        String eventType,
        Long orderId,
        String status,
        Long warehouseId,
        String customerName,
        String deliveryAddress,
        String deliveryCity,
        String deliveryPostalCode,
        String customerPhone,
        Instant occurredAt,
        UUID correlationId
) {
}
