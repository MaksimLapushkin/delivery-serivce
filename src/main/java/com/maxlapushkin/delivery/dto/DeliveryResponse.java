package com.maxlapushkin.delivery.dto;

import com.maxlapushkin.delivery.model.DeliveryStatus;
import java.time.Instant;

public record DeliveryResponse(
        Long id,
        Long orderId,
        DeliveryStatus status,
        String customerName,
        String deliveryAddress,
        String deliveryCity,
        String deliveryPostalCode,
        String customerPhone,
        Instant createdAt,
        Instant updatedAt,
        Instant deliveredAt,
        Instant returnedAt,
        Instant cancelledAt
) {
}
