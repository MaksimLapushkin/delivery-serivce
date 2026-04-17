package com.maxlapushkin.delivery.service;

import com.maxlapushkin.delivery.dto.DeliveryStateChangeResponse;
import com.maxlapushkin.delivery.exception.ResourceNotFoundException;
import com.maxlapushkin.delivery.model.Delivery;
import com.maxlapushkin.delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeliveryCommandService {

    private final DeliveryRepository deliveryRepository;
    // later:
    // private final DeliveryTimelineRepository deliveryTimelineRepository;
    // private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public DeliveryStateChangeResponse markInTransit(Long id) {
        Delivery delivery = getDeliveryOrThrow(id);

        // TODO:
        // validate transition: ACCEPTED -> IN_TRANSIT only
        // set status
        // set updatedAt
        // save timeline event later
        // save outbox event later

        return toStateChangeResponse(delivery);
    }

    @Transactional
    public DeliveryStateChangeResponse deliver(Long id) {
        Delivery delivery = getDeliveryOrThrow(id);

        // TODO:
        // validate transition: IN_TRANSIT -> DELIVERED only
        // set status
        // set updatedAt
        // set deliveredAt
        // save timeline event later
        // save outbox event later

        return toStateChangeResponse(delivery);
    }

    @Transactional
    public DeliveryStateChangeResponse returnDelivery(Long id) {
        Delivery delivery = getDeliveryOrThrow(id);

        // TODO:
        // validate transition: IN_TRANSIT -> RETURNED only
        // set status
        // set updatedAt
        // set returnedAt
        // save timeline event later
        // save outbox event later

        return toStateChangeResponse(delivery);
    }

    @Transactional
    public DeliveryStateChangeResponse cancel(Long id) {
        Delivery delivery = getDeliveryOrThrow(id);

        // TODO:
        // validate transition: ACCEPTED -> CANCELLED only
        // set status
        // set updatedAt
        // set cancelledAt
        // save timeline event later
        // save outbox event later

        return toStateChangeResponse(delivery);
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