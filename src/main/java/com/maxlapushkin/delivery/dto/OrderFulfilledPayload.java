package com.maxlapushkin.delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderFulfilledPayload(
        Long orderId,
        String status,
        Long warehouseId,
        String customerName,
        String deliveryAddress,
        String deliveryCity,
        String deliveryPostalCode,
        String customerPhone,
        List<OrderFulfilledLine> lines
) {
}
