package com.maxlapushkin.delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderFulfilledLine(
        Long productId,
        Integer quantity
) {
}
