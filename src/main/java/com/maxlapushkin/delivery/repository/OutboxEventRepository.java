package com.maxlapushkin.delivery.repository;

import com.maxlapushkin.delivery.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
}
