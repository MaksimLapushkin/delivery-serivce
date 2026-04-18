package com.maxlapushkin.delivery.repository;

import com.maxlapushkin.delivery.model.OutboxEvent;
import com.maxlapushkin.delivery.model.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
