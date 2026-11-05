package com.example.ordermanagement.persistence.repository;

import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.persistence.entity.Order;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    @Query(
        "select distinct o from Order o left join fetch o.items where o.id = :id"
    )
    Optional<Order> findWithItemsById(@Param("id") UUID id);

    @Query("select o.customerId from Order o where o.id = :id")
    Optional<UUID> findCustomerIdById(@Param("id") UUID id);

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Order> findByCustomerIdAndStatus(
        UUID customerId,
        OrderStatus status,
        Pageable pageable
    );

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
}
