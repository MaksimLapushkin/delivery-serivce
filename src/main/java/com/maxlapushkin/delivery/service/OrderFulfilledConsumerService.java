package com.maxlapushkin.delivery.service;

import com.maxlapushkin.delivery.dto.OrderFulfilledEvent;
import com.maxlapushkin.delivery.model.Delivery;
import com.maxlapushkin.delivery.model.DeliveryStatus;
import com.maxlapushkin.delivery.model.ProcessedEvent;
import com.maxlapushkin.delivery.repository.DeliveryRepository;
import com.maxlapushkin.delivery.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderFulfilledConsumerService {

    private final DeliveryRepository deliveryRepository;
    private final ProcessedEventRepository processedEventRepository;

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
        if (processedEventRepository.existsById(String.valueOf(event.eventId()))) {
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
        processedEventRepository.save(new ProcessedEvent(eventId, processedAt));

        log.info("Received order lifecycle event: type={}, orderId={}",
                event.eventType(), event.orderId());
    }
}