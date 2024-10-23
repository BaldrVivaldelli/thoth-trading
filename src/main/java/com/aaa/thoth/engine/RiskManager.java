package com.aaa.thoth.engine;

import com.aaa.thoth.core.Order;
import com.aaa.thoth.core.enums.OrderSide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;

public class RiskManager {
    private static final Logger logger = LoggerFactory.getLogger(RiskManager.class);

    // Límites por trader
    private static final double MAX_POSITION_VALUE = 5_000_000.0; // $5M por trader
    private static final double MAX_SINGLE_ORDER_VALUE = 1_000_000.0; // $1M por orden
    private static final int MAX_OPEN_ORDERS = 1000; // Por trader

    // Límites por símbolo
    private static final double MAX_SYMBOL_POSITION = 1_000_000.0; // $1M por símbolo
    private static final double MAX_PRICE_DEVIATION = 0.10; // 10% máximo de desviación

    // Tracking de posiciones
    private final Map<String, TraderPosition> traderPositions = new ConcurrentHashMap<>();
    private final Map<String, SymbolPosition> symbolPositions = new ConcurrentHashMap<>();

    // Clase para tracking de posición por trader
    private static class TraderPosition {
        final DoubleAdder buyValue = new DoubleAdder();
        final DoubleAdder sellValue = new DoubleAdder();
        final DoubleAdder netPosition = new DoubleAdder();
        final Map<String, DoubleAdder> symbolPositions = new ConcurrentHashMap<>();

        double getNetValue() {
            return netPosition.sum();
        }

        void updatePosition(Order order) {
            double orderValue = order.price() * order.quantity();
            if (order.side() == OrderSide.BUY) {
                buyValue.add(orderValue);
                netPosition.add(orderValue);
            } else {
                sellValue.add(orderValue);
                netPosition.add(-orderValue);
            }

            symbolPositions.computeIfAbsent(order.symbol(), k -> new DoubleAdder())
                    .add(order.side() == OrderSide.BUY ? orderValue : -orderValue);
        }
    }

    // Clase para tracking de posición por símbolo
    private static class SymbolPosition {
        final DoubleAdder totalValue = new DoubleAdder();
        volatile double lastPrice;
        volatile long lastUpdateTime;

        void updatePosition(double value, double price) {
            totalValue.add(value);
            lastPrice = price;
            lastUpdateTime = System.currentTimeMillis();
        }
    }

    public boolean checkRisk(Order order) {
        try {
            return validateSingleOrderRisk(order) &&
                    validateTraderRisk(order) &&
                    validateSymbolRisk(order) &&
                    validatePriceDeviation(order);
        } catch (Exception e) {
            logger.error("Error in risk check for order: {}", order, e);
            return false;
        }
    }

    private boolean validateSingleOrderRisk(Order order) {
        double orderValue = order.price() * order.quantity();
        if (orderValue > MAX_SINGLE_ORDER_VALUE) {
            logger.error("Order value {} exceeds maximum allowed {}",
                    orderValue, MAX_SINGLE_ORDER_VALUE);
            return false;
        }
        return true;
    }

    private boolean validateTraderRisk(Order order) {
        TraderPosition position = traderPositions.computeIfAbsent(
                order.traderId(), k -> new TraderPosition());

        // Verificar posición total
        double potentialPosition = position.getNetValue() +
                (order.side() == OrderSide.BUY ? 1 : -1) * order.price() * order.quantity();

        if (Math.abs(potentialPosition) > MAX_POSITION_VALUE) {
            logger.error("Trader position would exceed maximum allowed: {}",
                    potentialPosition);
            return false;
        }

        // Verificar posición por símbolo
        DoubleAdder symbolPosition = position.symbolPositions
                .computeIfAbsent(order.symbol(), k -> new DoubleAdder());
        double potentialSymbolPosition = symbolPosition.sum() +
                (order.side() == OrderSide.BUY ? 1 : -1) * order.price() * order.quantity();

        if (Math.abs(potentialSymbolPosition) > MAX_SYMBOL_POSITION) {
            logger.error("Symbol position would exceed maximum allowed: {}",
                    potentialSymbolPosition);
            return false;
        }

        return true;
    }

    private boolean validateSymbolRisk(Order order) {
        SymbolPosition position = symbolPositions.computeIfAbsent(
                order.symbol(), k -> new SymbolPosition());

        double orderValue = order.price() * order.quantity();
        double potentialValue = position.totalValue.sum() +
                (order.side() == OrderSide.BUY ? 1 : -1) * orderValue;

        if (Math.abs(potentialValue) > MAX_SYMBOL_POSITION) {
            logger.error("Symbol position would exceed maximum allowed: {}",
                    potentialValue);
            return false;
        }

        return true;
    }

    private boolean validatePriceDeviation(Order order) {
        SymbolPosition position = symbolPositions.get(order.symbol());
        if (position != null && position.lastPrice > 0) {
            double priceDeviation = Math.abs(order.price() - position.lastPrice) /
                    position.lastPrice;

            if (priceDeviation > MAX_PRICE_DEVIATION) {
                logger.error("Price deviation {} exceeds maximum allowed {}",
                        priceDeviation, MAX_PRICE_DEVIATION);
                return false;
            }
        }
        return true;
    }

    // Métodos para actualizar posiciones después de trades
    public void updatePositions(Order order) {
        // Actualizar posición del trader
        TraderPosition traderPosition = traderPositions.computeIfAbsent(
                order.traderId(), k -> new TraderPosition());
        traderPosition.updatePosition(order);

        // Actualizar posición del símbolo
        SymbolPosition symbolPosition = symbolPositions.computeIfAbsent(
                order.symbol(), k -> new SymbolPosition());
        symbolPosition.updatePosition(
                (order.side() == OrderSide.BUY ? 1 : -1) * order.price() * order.quantity(),
                order.price()
        );
    }
}