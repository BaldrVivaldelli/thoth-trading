package com.aaa.thoth.core;

import com.aaa.thoth.core.enums.OrderSide;
import java.time.Instant;
import java.util.UUID;

public record Trade(
        String tradeId,
        String symbol,
        Order makerOrder,      // Orden que ya estaba en el libro
        Order takerOrder,      // Orden que ejecutó contra el libro
        double price,
        long quantity,
        Instant timestamp,
        String exchangeId,
        boolean isBuyerMaker   // true si el maker es comprador
) {
    // Constructor compacto con validación
    public Trade {
        // Validar campos obligatorios
        if (tradeId == null || tradeId.isBlank()) {
            tradeId = UUID.randomUUID().toString();
        }
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be null or blank");
        }
        if (makerOrder == null) {
            throw new IllegalArgumentException("Maker order cannot be null");
        }
        if (takerOrder == null) {
            throw new IllegalArgumentException("Taker order cannot be null");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("Trade price must be positive");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Trade quantity must be positive");
        }

        // Validar coherencia entre órdenes
        if (!makerOrder.symbol().equals(takerOrder.symbol())) {
            throw new IllegalArgumentException("Orders must be for the same symbol");
        }
        if (!symbol.equals(makerOrder.symbol())) {
            throw new IllegalArgumentException("Trade symbol must match orders symbol");
        }
        if (makerOrder.side() == takerOrder.side()) {
            throw new IllegalArgumentException("Orders must be on opposite sides");
        }

        // Validar cantidades
        if (quantity > makerOrder.getRemainingQuantity() ||
                quantity > takerOrder.getRemainingQuantity()) {
            throw new IllegalArgumentException("Trade quantity exceeds remaining order quantity");
        }

        // Asignar valores por defecto si son null
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    // Factory method principal
    public static Trade createTrade(Order makerOrder, Order takerOrder, double price, long quantity) {
        // Determinar si el maker es comprador
        boolean isBuyerMaker = makerOrder.side() == OrderSide.BUY;

        return new Trade(
                UUID.randomUUID().toString(),
                makerOrder.symbol(),
                makerOrder,
                takerOrder,
                price,
                quantity,
                Instant.now(),
                null,           // exchangeId
                isBuyerMaker
        );
    }

    // Métodos de utilidad
    public double getTotal() {
        return price * quantity;
    }

    public Order getBuyOrder() {
        return isBuyerMaker ? makerOrder : takerOrder;
    }

    public Order getSellOrder() {
        return isBuyerMaker ? takerOrder : makerOrder;
    }

    public boolean isValid() {
        return quantity > 0 &&
                price > 0 &&
                makerOrder != null &&
                takerOrder != null &&
                makerOrder.side() != takerOrder.side();
    }

    // Método para crear una copia con exchangeId
    public Trade withExchangeId(String newExchangeId) {
        return new Trade(
                tradeId,
                symbol,
                makerOrder,
                takerOrder,
                price,
                quantity,
                timestamp,
                newExchangeId,
                isBuyerMaker
        );
    }

    @Override
    public String toString() {
        return String.format(
                "Trade{id=%s, symbol=%s, price=%.2f, qty=%d, maker=%s, taker=%s, timestamp=%s}",
                tradeId,
                symbol,
                price,
                quantity,
                makerOrder.orderId(),
                takerOrder.orderId(),
                timestamp
        );
    }

    // Métodos adicionales para información del trade
    public boolean isBuyerTaker() {
        return !isBuyerMaker;
    }

    public String getBuyOrderId() {
        return getBuyOrder().orderId();
    }

    public String getSellOrderId() {
        return getSellOrder().orderId();
    }

    public String getBuyTraderId() {
        return getBuyOrder().traderId();
    }

    public String getSellTraderId() {
        return getSellOrder().traderId();
    }

    // Métodos para análisis
    public double getPrice() {
        return price;
    }

    public long getQuantity() {
        return quantity;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}