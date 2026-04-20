package com.maxlapushkin.delivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxlapushkin.delivery.dto.DeliveryLifecycleEventPayload;
import com.maxlapushkin.delivery.model.OutboxEvent;
import com.maxlapushkin.delivery.model.OutboxStatus;
import com.maxlapushkin.delivery.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherService {

    private static final String DELIVERY_TOPIC = "delivery.lifecycle.v1";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, DeliveryLifecycleEventPayload> deliveryKafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void publishNewEvents() {
        List<OutboxEvent> events = outboxEventRepository
                .findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.NEW);

        for (OutboxEvent event : events) {
            try {
                DeliveryLifecycleEventPayload payload = deserializePayload(event.getPayloadJson());

                deliveryKafkaTemplate
                        .send(DELIVERY_TOPIC, event.getAggregateId(), payload)
                        .get();

                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
            } catch (Exception e) {
                log.error("Failed to publish outbox event id={}", event.getId(), e);
            }
        }
    }

    private DeliveryLifecycleEventPayload deserializePayload(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, DeliveryLifecycleEventPayload.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize outbox payload", e);
        }
    }
}