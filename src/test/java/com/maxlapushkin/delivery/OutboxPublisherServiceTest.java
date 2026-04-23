package com.maxlapushkin.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxlapushkin.delivery.dto.DeliveryLifecycleEventPayload;
import com.maxlapushkin.delivery.model.OutboxEvent;
import com.maxlapushkin.delivery.model.OutboxStatus;
import com.maxlapushkin.delivery.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.maxlapushkin.delivery.service.OutboxPublisherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:outbox_publisher_service_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.kafka.listener.auto-startup=false"
})
@Transactional
class OutboxPublisherServiceTest {

    private static final String DELIVERY_TOPIC = "delivery.lifecycle.v1";

    @Autowired
    private OutbogixPublisherService outboxPublisherService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KafkaTemplate<String, DeliveryLifecycleEventPayload> deliveryKafkaTemplate;

    @Test
    void publishNewEventsPublishesNewOutboxEventAndMarksItPublished() throws Exception {
        // given
        DeliveryLifecycleEventPayload payload = buildPayload(3001L, 4001L, "DELIVERY_ACCEPTED");
        OutboxEvent outboxEvent = createNewOutboxEvent("4001", payload);
        CompletableFuture<SendResult<String, DeliveryLifecycleEventPayload>> success =
                CompletableFuture.completedFuture(null);

        when(deliveryKafkaTemplate.send(
                eq(DELIVERY_TOPIC),
                eq(outboxEvent.getAggregateId()),
                any(DeliveryLifecycleEventPayload.class)
        )).thenReturn(success);

        // when
        outboxPublisherService.publishNewEvents();

        // then
        OutboxEvent updated = outboxEventRepository.findById(outboxEvent.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(updated.getPublishedAt()).isNotNull();
        assertThat(updated.getRetryCount()).isZero();
        assertThat(updated.getLastError()).isNull();

        verify(deliveryKafkaTemplate).send(
                eq(DELIVERY_TOPIC),
                eq(outboxEvent.getAggregateId()),
                any(DeliveryLifecycleEventPayload.class)
        );
    }

    @Test
    void publishNewEventsKeepsEventNewAndIncrementsRetryCountWhenKafkaSendFails() throws Exception {
        // given
        DeliveryLifecycleEventPayload payload = buildPayload(3002L, 4002L, "DELIVERY_IN_TRANSIT");
        OutboxEvent outboxEvent = createNewOutboxEvent("4002", payload);
        CompletableFuture<SendResult<String, DeliveryLifecycleEventPayload>> failure =
                CompletableFuture.failedFuture(new RuntimeException("kafka send failed"));

        when(deliveryKafkaTemplate.send(
                eq(DELIVERY_TOPIC),
                eq(outboxEvent.getAggregateId()),
                any(DeliveryLifecycleEventPayload.class)
        )).thenReturn(failure);

        // when
        outboxPublisherService.publishNewEvents();

        // then
        OutboxEvent updated = outboxEventRepository.findById(outboxEvent.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.NEW);
        assertThat(updated.getPublishedAt()).isNull();
        assertThat(updated.getRetryCount()).isEqualTo(1);
        assertThat(updated.getLastError()).contains("kafka send failed");
        assertThat(updated.getLastAttemptAt()).isNotNull();
    }

    @Test
    void publishNewEventsMarksEventFailedWhenMaxRetryCountIsReached() throws Exception {
        // given
        DeliveryLifecycleEventPayload payload = buildPayload(3003L, 4003L, "DELIVERY_DELIVERED");
        OutboxEvent outboxEvent = createNewOutboxEvent("4003", payload, 4);
        CompletableFuture<SendResult<String, DeliveryLifecycleEventPayload>> failure =
                CompletableFuture.failedFuture(new RuntimeException("kafka send failed"));

        when(deliveryKafkaTemplate.send(
                eq(DELIVERY_TOPIC),
                eq(outboxEvent.getAggregateId()),
                any(DeliveryLifecycleEventPayload.class)
        )).thenReturn(failure);

        // when
        outboxPublisherService.publishNewEvents();

        // then
        OutboxEvent updated = outboxEventRepository.findById(outboxEvent.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(updated.getPublishedAt()).isNull();
        assertThat(updated.getRetryCount()).isEqualTo(5);
        assertThat(updated.getLastError()).contains("kafka send failed");
        assertThat(updated.getLastAttemptAt()).isNotNull();
    }

    private OutboxEvent createNewOutboxEvent(String aggregateId, DeliveryLifecycleEventPayload payload)
            throws Exception {
        return createNewOutboxEvent(aggregateId, payload, 0);
    }

    private OutboxEvent createNewOutboxEvent(String aggregateId, DeliveryLifecycleEventPayload payload, int retryCount)
            throws Exception {
        return outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("DELIVERY")
                .aggregateId(aggregateId)
                .eventType(payload.eventType())
                .payloadJson(objectMapper.writeValueAsString(payload))
                .status(OutboxStatus.NEW)
                .createdAt(payload.occurredAt())
                .publishedAt(null)
                .retryCount(retryCount)
                .lastError(null)
                .lastAttemptAt(null)
                .build());
    }

    private DeliveryLifecycleEventPayload buildPayload(Long deliveryId, Long orderId, String eventType) {
        return new DeliveryLifecycleEventPayload(
                UUID.randomUUID(),
                deliveryId,
                orderId,
                Instant.now(),
                eventType,
                "ACCEPTED"
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DisableKafkaListenerAutoStartupConfig {

        @Bean
        static BeanPostProcessor kafkaListenerContainerFactoryPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName)
                        throws BeansException {
                    if (bean instanceof ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
                        factory.setAutoStartup(false);
                    }

                    return bean;
                }
            };
        }
    }
}
