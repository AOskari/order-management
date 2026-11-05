package com.example.ordermanagement.persistence.entity;

import com.example.ordermanagement.domain.model.PriceUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "order_item")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(
        name = "product_id",
        nullable = false,
        updatable = false,
        length = 64
    )
    private String productId;

    @Column(
        name = "product_name",
        nullable = false,
        updatable = false,
        length = 255
    )
    private String productName;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "price_unit",
        nullable = false,
        updatable = false,
        length = 16
    )
    private PriceUnit priceUnit;

    @Column(
        name = "unit_price",
        nullable = false,
        updatable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal unitPrice;

    @Column(nullable = false, updatable = false)
    private int quantity;
}
