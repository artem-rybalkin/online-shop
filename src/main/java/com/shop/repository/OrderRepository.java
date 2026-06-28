package com.shop.repository;

import com.shop.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUserUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    Page<Order> findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);
}
