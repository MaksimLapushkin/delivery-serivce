package com.maxlapushkin.delivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxlapushkin.delivery.dto.DeliveryLifecycleEventPayload;
import com.maxlapushkin.delivery.dto.OrderFulfilledEvent;
import com.maxlapushkin.delivery.model.*;
import com.maxlapushkin.delivery.repository.DeliveryRepository;
import com.maxlapushkin.delivery.repository.DeliveryTimelineRepository;
import com.maxlapushkin.delivery.repository.OutboxEventRepository;
import com.maxlapushkin.delivery.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderFulfilledConsumerService {

    private final DeliveryRepository deliveryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final DeliveryTimelineRepository deliveryTimelineRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "order.lifecycle.v1",
            groupId = "delivery-service",
            containerFactory = "orderFulfilledKafkaListenerContainerFactory"
    )
    @Transactional
    public void handleOrderLifecycleEvent(OrderFulfilledEvent event) {

        if (!"ORDER_FULFILLED".equals(event.eventType())) {
            return;
        }
        if (processedEventRepository.existsById(event.eventId().toString())) {
            return;
        }
        if (deliveryRepository.findByOrderId(event.orderId()).isPresent()) {
            processedEventRepository.save(new ProcessedEvent(event.eventId().toString(), Instant.now()));
            log.warn("Delivery already exists for orderId={}", event.orderId());
            return;
        }
        Delivery delivery = Delivery.builder()
                .id(null)
                .orderId(event.orderId())
                .status(DeliveryStatus.ACCEPTED)
                .customerName(event.customerName())
                .deliveryAddress(event.deliveryAddress())
                .deliveryCity(event.deliveryCity())
                .deliveryPostalCode(event.deliveryPostalCode())
                .customerPhone(event.customerPhone())
                .createdAt(event.occurredAt())
                .updatedAt(event.occurredAt())
                .deliveredAt(null)
                .returnedAt(null)
                .cancelledAt(null)
                .build();
        deliveryRepository.save(delivery);
        String eventId = event.eventId().toString();
        Instant processedAt = Instant.now();
        Instant now = event.occurredAt();

        DeliveryLifecycleEventPayload payload = new DeliveryLifecycleEventPayload(
                UUID.randomUUID(),
                delivery.getId(),
                delivery.getOrderId(),
                now,
                "DELIVERY_ACCEPTED",
                delivery.getStatus().name()
        );

        deliveryTimelineRepository.save(new DeliveryTimeline(
                null,
                delivery.getId(),
                delivery.getOrderId(),
                payload.eventId().toString(),
                payload.eventType(),
                payload.status(),
                payload.occurredAt(),
                toJson(payload)
        ));

        outboxEventRepository.save(
                OutboxEvent.builder()
                        .aggregateType("DELIVERY")
                        .aggregateId(delivery.getId().toString())
                        .eventType(payload.eventType())
                        .payloadJson(toJson(payload))
                        .status(OutboxStatus.NEW)
                        .createdAt(payload.occurredAt())
                        .publishedAt(null)
                        .build()
        );
        processedEventRepository.save(new ProcessedEvent(eventId, processedAt));
        log.info("Created delivery id={} for orderId={} with status={}",
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getStatus());
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize payload", e);
        }
    }
}