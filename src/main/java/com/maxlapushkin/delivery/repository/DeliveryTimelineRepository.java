package com.maxlapushkin.delivery.repository;

import com.maxlapushkin.delivery.model.DeliveryTimeline;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryTimelineRepository extends JpaRepository<DeliveryTimeline, Long> {

    List<DeliveryTimeline> findByDeliveryIdOrderByOccurredAtAsc(Long deliveryId);
}
