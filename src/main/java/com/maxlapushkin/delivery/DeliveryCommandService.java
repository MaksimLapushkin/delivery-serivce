package com.maxlapushkin.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxlapushkin.delivery.dto.DeliveryLifecycleEventPayload;
import com.maxlapushkin.delivery.dto.DeliveryStateChangeResponse;
import com.maxlapushkin.delivery.exception.InvalidDeliveryStateException;
import com.maxlapushkin.delivery.exception.ResourceNotFoundException;
import com.maxlapushkin.delivery.model.*;
import com.maxlapushkin.delivery.repository.DeliveryRepository;
import com.maxlapushkin.delivery.repository.DeliveryTimelineRepository;
import com.maxlapushkin.delivery.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryCommandService {

    private final DeliveryRepository deliveryRepository;

    private final DeliveryTimelineRepository deliveryTimelineRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public DeliveryStateChangeResponse markInTransit(Long id) {
        Instant now = Instant.now();
        Delivery delivery = getDeliveryOrThrow(id);

        if (delivery.getStatus() != DeliveryStatus.ACCEPTED) {
            throw new InvalidDeliveryStateException(
                    "Cannot transition delivery " + id + " from " + delivery.getStatus() + " to IN_TRANSIT"
            );
        }
        delivery.setStatus(DeliveryStatus.IN_TRANSIT);
        delivery.setUpdatedAt(now);
        DeliveryLifecycleEventPayload payload =
                buildPayload(delivery, "DELIVERY_IN_TRANSIT", now);

        saveTimeline(delivery, payload);
        saveOutbox(delivery, payload);

        return toStateChangeResponse(delivery);
    }

    @Transactional
    public DeliveryStateChangeResponse deliver(Long id) {
        Instant now = Instant.now();
        Delivery delivery = getDeliveryOrThrow(id);

        if (delivery.getStatus() != DeliveryStatus.IN_TRANSIT) {
            throw new InvalidDeliveryStateException(
                    "Cannot transition delivery " + id + " from " + delivery.getStatus() + " to DELIVERED"
            );
        }
        delivery.setStatus(DeliveryStatus.DELIVERED);
        delivery.setUpdatedAt(now);
        delivery.setDeliveredAt(now);
        DeliveryLifecycleEventPayload payload =
                buildPayload(delivery, "DELIVERY_DELIVERED", now);

        saveTimeline(delivery, payload);
        saveOutbox(delivery, payload);

        return toStateChangeResponse(delivery);
    }

    @Transactional
    public DeliveryStateChangeResponse returnDelivery(Long id) {
        Instant now = Instant.now();
        Delivery delivery = getDeliveryOrThrow(id);

        if (delivery.getStatus() != DeliveryStatus.IN_TRANSIT) {
            throw new InvalidDeliveryStateException(
                    "Cannot transition delivery " + id + " from " + delivery.getStatus() + " to RETURNED"
            );
        }
        delivery.setStatus(DeliveryStatus.RETURNED);
        delivery.setUpdatedAt(now);
        delivery.setReturnedAt(now);
        DeliveryLifecycleEventPayload payload =
                buildPayload(delivery, "DELIVERY_RETURNED", now);

        saveTimeline(delivery, payload);
        saveOutbox(delivery, payload);

        return toStateChangeResponse(delivery);
    }

    @Transactional
    public DeliveryStateChangeResponse cancel(Long id) {
        Instant now = Instant.now();
        Delivery delivery = getDeliveryOrThrow(id);

        if(delivery.getStatus() != DeliveryStatus.ACCEPTED) {
            throw new InvalidDeliveryStateException(
                    "Cannot transition delivery " + id + " from " + delivery.getStatus() + " to CANCELLED"
            );
        }
        delivery.setStatus(DeliveryStatus.CANCELLED);
        delivery.setUpdatedAt(now);
        delivery.setCancelledAt(now);
        DeliveryLifecycleEventPayload payload =
                buildPayload(delivery, "DELIVERY_CANCELLED", now);

        saveTimeline(delivery, payload);
        saveOutbox(delivery, payload);

        return toStateChangeResponse(delivery);
    }

    private void saveTimeline(Delivery delivery, DeliveryLifecycleEventPayload payload) {
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
    }

    private void saveOutbox(Delivery delivery, DeliveryLifecycleEventPayload payload) {
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateType("DELIVERY")
                .aggregateId(delivery.getId().toString())
                .eventType(payload.eventType())
                .payloadJson(toJson(payload))
                .status(OutboxStatus.NEW)
                .createdAt(payload.occurredAt())
                .publishedAt(null)
                .retryCount(0)
                .lastError(null)
                .lastAttemptAt(null)
                .build();

        outboxEventRepository.save(outboxEvent);
    }

    private DeliveryLifecycleEventPayload buildPayload(Delivery delivery, String eventType, Instant occurredAt){
        return new DeliveryLifecycleEventPayload(
                UUID.randomUUID(),
                delivery.getId(),
                delivery.getOrderId(),
                occurredAt,
                eventType,
                delivery.getStatus().name()
        );
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Delivery getDeliveryOrThrow(Long id) {
        return deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + id));
    }

    private DeliveryStateChangeResponse toStateChangeResponse(Delivery delivery) {
        return new DeliveryStateChangeResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getStatus(),
                delivery.getUpdatedAt()
        );
    }
}
