package com.maxlapushkin.delivery.dto;

import com.maxlapushkin.delivery.model.DeliveryStatus;
import java.time.Instant;

public record DeliveryStateChangeResponse(
        Long id,
        Long orderId,
        DeliveryStatus status,
        Instant updatedAt
) {
}