package com.maxlapushkin.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.maxlapushkin.delivery.dto.OrderFulfilledEvent;
import com.maxlapushkin.delivery.dto.OrderFulfilledPayload;
import com.maxlapushkin.delivery.model.Delivery;
import com.maxlapushkin.delivery.model.DeliveryStatus;
import com.maxlapushkin.delivery.model.DeliveryTimeline;
import com.maxlapushkin.delivery.model.OutboxEvent;
import com.maxlapushkin.delivery.repository.DeliveryRepository;
import com.maxlapushkin.delivery.repository.DeliveryTimelineRepository;
import com.maxlapushkin.delivery.repository.OutboxEventRepository;
import com.maxlapushkin.delivery.repository.ProcessedEventRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.maxlapushkin.delivery.service.OrderFulfilledConsumerService;
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
        "spring.datasource.url=jdbc:h2:mem:order_fulfilled_consumer_service_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.kafka.listener.auto-startup=false"
})
@Transactional
class OrderFulfilledConsumerServiceTest {

    @Autowired
    private OrderFulfilledConsumerService orderFulfilledConsumerService;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private DeliveryTimelineRepository deliveryTimelineRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void orderFulfilledCreatesAcceptedDeliveryAndWritesTimelineOutboxAndProcessedEvent() {
        // given
        OrderFulfilledEvent event = buildOrderFulfilledEvent(UUID.randomUUID(), 2001L);

        // when
        orderFulfilledConsumerService.handleOrderLifecycleEvent(event);

        // then
        Delivery delivery = deliveryRepository.findByOrderId(event.payload().orderId()).orElseThrow();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.ACCEPTED);
        assertThat(delivery.getCustomerName()).isEqualTo(event.payload().customerName());
        assertThat(delivery.getDeliveryAddress()).isEqualTo(event.payload().deliveryAddress());
        assertThat(delivery.getDeliveryCity()).isEqualTo(event.payload().deliveryCity());
        assertThat(delivery.getDeliveryPostalCode()).isEqualTo(event.payload().deliveryPostalCode());
        assertThat(delivery.getCustomerPhone()).isEqualTo(event.payload().customerPhone());

        assertThat(deliveryTimelineRepository.findByDeliveryIdOrderByOccurredAtAsc(delivery.getId()))
                .hasSize(1);
        assertThat(latestTimelineForDelivery(delivery.getId()).getEventType())
                .isEqualTo("DELIVERY_ACCEPTED");

        assertThat(outboxEventsForDelivery(delivery.getId()))
                .hasSize(1);
        assertThat(latestOutboxEvent(delivery.getId()).getEventType())
                .isEqualTo("DELIVERY_ACCEPTED");

        assertThat(processedEventRepository.existsById(event.eventId().toString())).isTrue();
    }

    @Test
    void duplicateSameEventIdIsIgnored() {
        // given
        OrderFulfilledEvent event = buildOrderFulfilledEvent(UUID.randomUUID(), 2002L);

        // when
        orderFulfilledConsumerService.handleOrderLifecycleEvent(event);
        orderFulfilledConsumerService.handleOrderLifecycleEvent(event);

        // then
        Delivery delivery = deliveryRepository.findByOrderId(event.payload().orderId()).orElseThrow();
        assertThat(deliveryRepository.count()).isEqualTo(1);
        assertThat(processedEventRepository.count()).isEqualTo(1);
        assertThat(deliveryTimelineRepository.findByDeliveryIdOrderByOccurredAtAsc(delivery.getId()))
                .hasSize(1);
        assertThat(outboxEventsForDelivery(delivery.getId()))
                .hasSize(1);
    }

    @Test
    void differentEventIdWithSameOrderIdDoesNotCreateSecondDelivery() {
        // given
        Long orderId = 2003L;
        OrderFulfilledEvent firstEvent = buildOrderFulfilledEvent(UUID.randomUUID(), orderId);
        OrderFulfilledEvent duplicateOrderEvent = buildOrderFulfilledEvent(UUID.randomUUID(), orderId);

        // when
        orderFulfilledConsumerService.handleOrderLifecycleEvent(firstEvent);
        orderFulfilledConsumerService.handleOrderLifecycleEvent(duplicateOrderEvent);

        // then
        Delivery delivery = deliveryRepository.findByOrderId(orderId).orElseThrow();
        assertThat(deliveryRepository.count()).isEqualTo(1);
        assertThat(deliveryTimelineRepository.findByDeliveryIdOrderByOccurredAtAsc(delivery.getId()))
                .hasSize(1);
        assertThat(outboxEventsForDelivery(delivery.getId()))
                .hasSize(1);
    }

    @Test
    void nonOrderFulfilledEventTypeIsIgnored() {
        // given
        OrderFulfilledEvent event = new OrderFulfilledEvent(
                UUID.randomUUID(),
                2004L,
                "order-2004",
                BigDecimal.valueOf(1776935800.695920100),
                "ORDER_CANCELLED",
                new OrderFulfilledPayload(
                        2004L,
                        "CANCELLED",
                        9001L,
                        "Jane Customer",
                        "123 Test Street",
                        "Budapest",
                        "1011",
                        "+36123456789",
                        List.of()
                )
        );

        // when
        orderFulfilledConsumerService.handleOrderLifecycleEvent(event);

        // then
        assertThat(deliveryRepository.findByOrderId(event.payload().orderId())).isEmpty();
        assertThat(processedEventRepository.count()).isZero();
        assertThat(deliveryTimelineRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
    }

    private OrderFulfilledEvent buildOrderFulfilledEvent(UUID eventId, Long orderId) {
        return new OrderFulfilledEvent(
                eventId,
                orderId,
                "order-" + orderId,
                BigDecimal.valueOf(1776935800.695920100),
                "ORDER_FULFILLED",
                new OrderFulfilledPayload(
                        orderId,
                        "FULFILLED",
                        9001L,
                        "Jane Customer",
                        "123 Test Street",
                        "Budapest",
                        "1011",
                        "+36123456789",
                        List.of()
                )
        );
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
