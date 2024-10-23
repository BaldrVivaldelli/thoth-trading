package com.aaa.thoth.core;

import com.aaa.thoth.core.enums.OrderSide;
import com.aaa.thoth.core.enums.OrderStatus;
import com.aaa.thoth.core.enums.OrderType;
import java.time.Instant;
import java.util.UUID;

public record Order(
        String orderId,
        String symbol,
        OrderType type,
        OrderSide side,
        double price,
        double stopPrice,
        long quantity,
        long filledQuantity,
        long displayQuantity,
        String traderId,
        OrderStatus status,
        String exchangeId,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        int priority,
        String clientOrderId
) {
    // Constructor compacto con validación
    public Order {
        // Validar campos obligatorios
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be null or blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Order type cannot be null");
        }
        if (side == null) {
            throw new IllegalArgumentException("Order side cannot be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (traderId == null || traderId.isBlank()) {
            throw new IllegalArgumentException("Trader ID cannot be null or blank");
        }
        if (type.requiresPrice() && price <= 0) {
            throw new IllegalArgumentException("Price must be positive for " + type);
        }
        if ((type == OrderType.STOP || type == OrderType.STOP_LIMIT) && stopPrice <= 0) {
            throw new IllegalArgumentException("Stop price must be positive for " + type);
        }
        if (filledQuantity > quantity) {
            throw new IllegalArgumentException("Filled quantity cannot be greater than total quantity");
        }
        if (displayQuantity > quantity) {
            throw new IllegalArgumentException("Display quantity cannot be greater than total quantity");
        }

        // Asignar valores por defecto si son null
        if (orderId == null) {
            orderId = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = OrderStatus.NEW;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    // Factory methods
    public static Order limitOrder(String symbol, OrderSide side, double price, long quantity, String traderId) {
        return new Order(
                UUID.randomUUID().toString(), // orderId
                symbol,
                OrderType.LIMIT,
                side,
                price,
                0.0,            // stopPrice
                quantity,
                0L,             // filledQuantity
                quantity,       // displayQuantity
                traderId,
                OrderStatus.NEW,
                null,           // exchangeId
                Instant.now(),  // createdAt
                Instant.now(),  // updatedAt
                null,           // expiresAt
                0,             // priority
                null           // clientOrderId
        );
    }

    public static Order marketOrder(String symbol, OrderSide side, long quantity, String traderId) {
        return new Order(
                UUID.randomUUID().toString(),
                symbol,
                OrderType.MARKET,
                side,
                0.0,    // No price for market orders
                0.0,    // stopPrice
                quantity,
                0L,     // filledQuantity
                quantity,
                traderId,
                OrderStatus.NEW,
                null,
                Instant.now(),
                Instant.now(),
                null,
                0,
                null
        );
    }

    // Métodos de utilidad
    public boolean isComplete() {
        return filledQuantity == quantity;
    }

    public long getRemainingQuantity() {
        return quantity - filledQuantity;
    }

    public boolean isActive() {
        return !status.isFinal();
    }

    // Método para crear una nueva orden con estado actualizado
    public Order withStatus(OrderStatus newStatus) {
        return new Order(
                orderId, symbol, type, side, price, stopPrice, quantity, filledQuantity,
                displayQuantity, traderId, newStatus, exchangeId, createdAt, Instant.now(),
                expiresAt, priority, clientOrderId
        );
    }

    // Método para crear una nueva orden con cantidad ejecutada actualizada
    public Order withFilledQuantity(long newFilledQuantity) {
        return new Order(
                orderId, symbol, type, side, price, stopPrice, quantity, newFilledQuantity,
                displayQuantity, traderId, status, exchangeId, createdAt, Instant.now(),
                expiresAt, priority, clientOrderId
        );
    }

    @Override
    public String toString() {
        return String.format(
                "Order{id=%s, symbol=%s, type=%s, side=%s, price=%.2f, qty=%d/%d, status=%s}",
                orderId, symbol, type, side, price, filledQuantity, quantity, status
        );
    }
}