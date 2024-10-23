package com.aaa.thoth.engine;

import com.aaa.thoth.core.Order;
import com.aaa.thoth.core.enums.OrderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class OrderValidator {
    private static final Logger logger = LoggerFactory.getLogger(OrderValidator.class);

    // Cache de símbolos válidos
    private final Set<String> validSymbols = ConcurrentHashMap.newKeySet();

    // Configuración de límites
    private static final double MAX_ORDER_VALUE = 1_000_000.0; // $1M por orden
    private static final double MIN_ORDER_VALUE = 0.01; // 1 centavo mínimo
    private static final long MAX_ORDER_QUANTITY = 1_000_000; // 1M unidades
    private static final int MAX_ORDERS_PER_SECOND = 100; // Por trader
    private static final int PRICE_DECIMAL_PLACES = 2;

    public OrderValidator() {
        // Inicializar símbolos válidos
        initializeValidSymbols();
    }

    public boolean validate(Order order) {
        try {
            return validateBasicFields(order) &&
                    validateSymbol(order) &&
                    validatePrice(order) &&
                    validateQuantity(order) &&
                    validateOrderValue(order) &&
                    validateTiming(order) &&
                    validateSpecificOrderType(order);
        } catch (Exception e) {
            logger.error("Error validating order: {}", order, e);
            return false;
        }
    }

    private boolean validateBasicFields(Order order) {
        return order != null &&
                order.orderId() != null &&
                !order.orderId().isBlank() &&
                order.traderId() != null &&
                !order.traderId().isBlank() &&
                order.type() != null &&
                order.side() != null;
    }

    private boolean validateSymbol(Order order) {
        return validSymbols.contains(order.symbol());
    }

    private boolean validatePrice(Order order) {
        // Validar precio según tipo de orden
        if (order.type() == OrderType.MARKET) {
            return true; // Las órdenes de mercado no requieren precio
        }

        // Verificar que el precio tenga los decimales correctos
        double multiplier = Math.pow(10, PRICE_DECIMAL_PLACES);
        double normalizedPrice = order.price() * multiplier;
        if (Math.abs(normalizedPrice - Math.round(normalizedPrice)) > 0.00001) {
            logger.error("Invalid price decimals for order: {}", order);
            return false;
        }

        // Validar precio > 0 para órdenes límite
        return order.price() > 0;
    }

    private boolean validateQuantity(Order order) {
        return order.quantity() > 0 &&
                order.quantity() <= MAX_ORDER_QUANTITY &&
                order.quantity() == Math.floor(order.quantity()); // Debe ser un número entero
    }

    private boolean validateOrderValue(Order order) {
        double orderValue = order.price() * order.quantity();
        return orderValue >= MIN_ORDER_VALUE && orderValue <= MAX_ORDER_VALUE;
    }

    private boolean validateTiming(Order order) {
        // Validar que la orden no esté expirada
        if (order.expiresAt() != null && order.expiresAt().isBefore(Instant.now())) {
            logger.error("Order expired: {}", order);
            return false;
        }

        // Validar rate limiting por trader
        return validateRateLimit(order.traderId());
    }

    private boolean validateSpecificOrderType(Order order) {
        return switch (order.type()) {
            case MARKET -> validateMarketOrder(order);
            case LIMIT -> validateLimitOrder(order);
            case STOP, STOP_LIMIT -> validateStopOrder(order);
            case IOC -> validateIocOrder(order);
            case FOK -> validateFokOrder(order);
            case ICEBERG -> validateIcebergOrder(order);
        };
    }

    private boolean validateMarketOrder(Order order) {
        // Las órdenes de mercado no requieren precio
        return true;
    }

    private boolean validateLimitOrder(Order order) {
        return order.price() > 0;
    }

    private boolean validateStopOrder(Order order) {
        return order.stopPrice() > 0 &&
                (order.type() != OrderType.STOP_LIMIT || order.price() > 0);
    }

    private boolean validateIocOrder(Order order) {
        return order.price() > 0;
    }

    private boolean validateFokOrder(Order order) {
        return order.price() > 0;
    }

    private boolean validateIcebergOrder(Order order) {
        return order.price() > 0 &&
                order.displayQuantity() > 0 &&
                order.displayQuantity() <= order.quantity();
    }

    private void initializeValidSymbols() {
        // Aquí se cargarían los símbolos válidos desde una configuración o base de datos
        validSymbols.add("AAPL");
        validSymbols.add("GOOGL");
        validSymbols.add("MSFT");
        // etc.
    }

    private boolean validateRateLimit(String traderId) {
        // Implementar rate limiting por trader
        return true; // Implementación simplificada
    }
}