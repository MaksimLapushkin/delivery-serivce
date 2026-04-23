package com.maxlapushkin.delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderFulfilledEvent(
        UUID eventId,
        Long aggregateId,
        String correlationId,
        BigDecimal occurredAt,
        String eventType,
        OrderFulfilledPayload payload
) {
}
