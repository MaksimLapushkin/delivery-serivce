package com.maxlapushkin.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.maxlapushkin.delivery.exception.InvalidDeliveryStateException;
import com.maxlapushkin.delivery.model.Delivery;
import com.maxlapushkin.delivery.model.DeliveryStatus;
import com.maxlapushkin.delivery.model.DeliveryTimeline;
import com.maxlapushkin.delivery.model.OutboxEvent;
import com.maxlapushkin.delivery.model.OutboxStatus;
import com.maxlapushkin.delivery.repository.DeliveryRepository;
import com.maxlapushkin.delivery.repository.DeliveryTimelineRepository;
import com.maxlapushkin.delivery.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:delivery_command_service_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.kafka.listener.auto-startup=false"
})
@Transactional
class DeliveryCommandServiceTest {

    @Autowired
    private DeliveryCommandService deliveryCommandService;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryTimelineRepository deliveryTimelineRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void markInTransitAcceptedDeliveryUpdatesStatusAndWritesTimelineAndOutbox() {
        // given
        Delivery delivery = createAcceptedDelivery(1001L);
        Instant originalUpdatedAt = delivery.getUpdatedAt();

        // when
        deliveryCommandService.markInTransit(delivery.getId());

        // then
        Delivery updated = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
        assertThat(updated.getUpdatedAt()).isAfter(originalUpdatedAt);

        assertThat(deliveryTimelineRepository.findByDeliveryIdOrderByOccurredAtAsc(delivery.getId()))
                .hasSize(1);
        assertThat(latestTimelineForDelivery(delivery.getId()).getEventType())
                .isEqualTo("DELIVERY_IN_TRANSIT");

        assertThat(outboxEventsForDelivery(delivery.getId()))
                .hasSize(1);
        OutboxEvent outboxEvent = latestOutboxEvent(delivery.getId());
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.NEW);
        assertThat(outboxEvent.getEventType()).isEqualTo("DELIVERY_IN_TRANSIT");
    }

    @Test
    void cancelAcceptedDeliveryUpdatesStatusAndWritesTimelineAndOutbox() {
        // given
        Delivery delivery = createAcceptedDelivery(1002L);

        // when
        deliveryCommandService.cancel(delivery.getId());

        // then
        Delivery updated = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        assertThat(updated.getCancelledAt()).isNotNull();

        assertThat(deliveryTimelineRepository.findByDeliveryIdOrderByOccurredAtAsc(delivery.getId()))
                .hasSize(1);
        assertThat(latestTimelineForDelivery(delivery.getId()).getEventType())
                .isEqualTo("DELIVERY_CANCELLED");

        assertThat(outboxEventsForDelivery(delivery.getId()))
                .hasSize(1);
        OutboxEvent outboxEvent = latestOutboxEvent(delivery.getId());
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.NEW);
        assertThat(outboxEvent.getEventType()).isEqualTo("DELIVERY_CANCELLED");
    }

    @Test
    void deliverInTransitDeliveryUpdatesStatusAndWritesTimelineAndOutbox() {
        // given
        Delivery delivery = createDelivery(1003L, DeliveryStatus.IN_TRANSIT);

        // when
        deliveryCommandService.deliver(delivery.getId());

        // then
        Delivery updated = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(updated.getDeliveredAt()).isNotNull();

        assertThat(deliveryTimelineRepository.findByDeliveryIdOrderByOccurredAtAsc(delivery.getId()))
                .hasSize(1);
        assertThat(latestTimelineForDelivery(delivery.getId()).getEventType())
                .isEqualTo("DELIVERY_DELIVERED");

        assertThat(outboxEventsForDelivery(delivery.getId()))
                .hasSize(1);
        OutboxEvent outboxEvent = latestOutboxEvent(delivery.getId());
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.NEW);
        assertThat(outboxEvent.getEventType()).isEqualTo("DELIVERY_DELIVERED");
    }

    @Test
    void returnInTransitDeliveryUpdatesStatusAndWritesTimelineAndOutbox() {
        // given
        Delivery delivery = createDelivery(1004L, DeliveryStatus.IN_TRANSIT);

        // when
        deliveryCommandService.returnDelivery(delivery.getId());

        // then
        Delivery updated = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(DeliveryStatus.RETURNED);
        assertThat(updated.getReturnedAt()).isNotNull();

        assertThat(deliveryTimelineRepository.findByDeliveryIdOrderByOccurredAtAsc(delivery.getId()))
                .hasSize(1);
        assertThat(latestTimelineForDelivery(delivery.getId()).getEventType())
                .isEqualTo("DELIVERY_RETURNED");

        assertThat(outboxEventsForDelivery(delivery.getId()))
                .hasSize(1);
        OutboxEvent outboxEvent = latestOutboxEvent(delivery.getId());
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.NEW);
        assertThat(outboxEvent.getEventType()).isEqualTo("DELIVERY_RETURNED");
    }

    @Test
    void deliverAcceptedDeliveryThrowsInvalidDeliveryStateException() {
        // given
        Delivery delivery = createAcceptedDelivery(1005L);

        // when / then
        assertThatThrownBy(() -> deliveryCommandService.deliver(delivery.getId()))
                .isInstanceOf(InvalidDeliveryStateException.class);
    }

    @Test
    void cancelDeliveredDeliveryThrowsInvalidDeliveryStateException() {
        // given
        Delivery delivery = createDelivery(1006L, DeliveryStatus.DELIVERED);

        // when / then
        assertThatThrownBy(() -> deliveryCommandService.cancel(delivery.getId()))
                .isInstanceOf(InvalidDeliveryStateException.class);
    }

    private Delivery createAcceptedDelivery(Long orderId) {
        return createDelivery(orderId, DeliveryStatus.ACCEPTED);
    }

    private Delivery createDelivery(Long orderId, DeliveryStatus status) {
        Instant timestamp = Instant.now().minusSeconds(60);

        return deliveryRepository.save(Delivery.builder()
                .orderId(orderId)
                .status(status)
                .customerName("Jane Customer")
                .deliveryAddress("123 Test Street")
                .deliveryCity("Budapest")
                .deliveryPostalCode("1011")
                .customerPhone("+36123456789")
                .createdAt(timestamp)
                .updatedAt(timestamp)
                .build());
    }

    private DeliveryTimeline latestTimelineForDelivery(Long deliveryId) {
        List<DeliveryTimeline> timelines =
                deliveryTimelineRepository.findByDeliveryIdOrderByOccurredAtAsc(deliveryId);

        return timelines.get(timelines.size() - 1);
    }

    private List<OutboxEvent> outboxEventsForDelivery(Long deliveryId) {
        return outboxEventRepository.findAll().stream()
                .filter(outboxEvent -> deliveryId.toString().equals(outboxEvent.getAggregateId()))
                .toList();
    }

    private OutboxEvent latestOutboxEvent(Long deliveryId) {
        return outboxEventsForDelivery(deliveryId).stream()
                .max(Comparator.comparing(OutboxEvent::getCreatedAt))
                .orElseThrow();
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
