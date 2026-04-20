package com.maxlapushkin.delivery;

import com.maxlapushkin.delivery.dto.DeliveryResponse;
import com.maxlapushkin.delivery.dto.DeliveryTimelineResponse;
import com.maxlapushkin.delivery.exception.ResourceNotFoundException;
import com.maxlapushkin.delivery.model.Delivery;
import com.maxlapushkin.delivery.model.DeliveryTimeline;
import com.maxlapushkin.delivery.repository.DeliveryRepository;
import com.maxlapushkin.delivery.repository.DeliveryTimelineRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeliveryQueryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryTimelineRepository deliveryTimelineRepository;

    @Transactional(readOnly = true)
    public DeliveryResponse findById(Long id) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + id));
        return toResponse(delivery);
    }

    @Transactional(readOnly = true)
    public DeliveryResponse findByOrderId(Long orderId) {
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with orderId: " + orderId));
        return toResponse(delivery);
    }

    @Transactional(readOnly = true)
    public List<DeliveryTimelineResponse> getTimeline(Long deliveryId) {
        if (!deliveryRepository.existsById(deliveryId)) {
            throw new ResourceNotFoundException("Delivery not found with id: " + deliveryId);
        }

        return deliveryTimelineRepository.findByDeliveryIdOrderByOccurredAtAsc(deliveryId)
                .stream()
                .map(this::toTimelineResponse)
                .toList();
    }

    private DeliveryResponse toResponse(Delivery delivery) {
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getStatus(),
                delivery.getCustomerName(),
                delivery.getDeliveryAddress(),
                delivery.getDeliveryCity(),
                delivery.getDeliveryPostalCode(),
                delivery.getCustomerPhone(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt(),
                delivery.getDeliveredAt(),
                delivery.getReturnedAt(),
                delivery.getCancelledAt()
        );
    }

    private DeliveryTimelineResponse toTimelineResponse(DeliveryTimeline timeline) {
        return new DeliveryTimelineResponse(
                timeline.getId(),
                timeline.getDeliveryId(),
                timeline.getOrderId(),
                timeline.getEventId(),
                timeline.getEventType(),
                timeline.getStatus(),
                timeline.getOccurredAt(),
                timeline.getPayloadJson()
        );
    }
}
