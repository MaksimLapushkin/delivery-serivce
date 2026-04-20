package com.maxlapushkin.delivery;

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
    private static final int MAX_RETRY_COUNT = 5;

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
                event.setLastAttemptAt(Instant.now());
                DeliveryLifecycleEventPayload payload = deserializePayload(event.getPayloadJson());

                deliveryKafkaTemplate
                        .send(DELIVERY_TOPIC, event.getAggregateId(), payload)
                        .get();

                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                event.setLastError(null);

                log.info("Published outbox event id={} aggregateId={} eventType={}",
                        event.getId(),
                        event.getAggregateId(),
                        event.getEventType());
            } catch (Exception e) {
                int retryCount = currentRetryCount(event) + 1;
                event.setRetryCount(retryCount);
                event.setLastError(shortErrorMessage(e));

                log.warn("Failed to publish outbox event id={} aggregateId={} eventType={} retryCount={}",
                        event.getId(),
                        event.getAggregateId(),
                        event.getEventType(),
                        retryCount,
                        e);

                if (retryCount >= MAX_RETRY_COUNT) {
                    event.setStatus(OutboxStatus.FAILED);
                    log.error("Marked outbox event id={} aggregateId={} eventType={} as FAILED after retryCount={}",
                            event.getId(),
                            event.getAggregateId(),
                            event.getEventType(),
                            retryCount);
                }
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

    private int currentRetryCount(OutboxEvent event) {
        return event.getRetryCount() == null ? 0 : event.getRetryCount();
    }

    private String shortErrorMessage(Exception e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        String message = cause.getMessage();

        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }

        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
