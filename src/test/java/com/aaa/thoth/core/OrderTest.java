package com.aaa.thoth.core;

import com.aaa.thoth.core.enums.OrderSide;
import com.aaa.thoth.core.enums.OrderStatus;
import com.aaa.thoth.core.enums.OrderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Order Tests")
class OrderTest {

    @Nested
    @DisplayName("Factory Methods Tests")
    class FactoryMethodsTest {

        @Test
        @DisplayName("Should create valid limit order")
        void shouldCreateValidLimitOrder() {
            // When
            Order order = Order.limitOrder(
                    "AAPL",
                    OrderSide.BUY,
                    150.0,
                    100L,
                    "TRADER1"
            );

            // Then
            assertThat(order).isNotNull();
            assertThat(order.symbol()).isEqualTo("AAPL");
            assertThat(order.side()).isEqualTo(OrderSide.BUY);
            assertThat(order.price()).isEqualTo(150.0);
            assertThat(order.quantity()).isEqualTo(100L);
            assertThat(order.traderId()).isEqualTo("TRADER1");
            assertThat(order.type()).isEqualTo(OrderType.LIMIT);
            assertThat(order.status()).isEqualTo(OrderStatus.NEW);
            assertThat(order.orderId()).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("Should create valid market order")
        void shouldCreateValidMarketOrder() {
            // When
            Order order = Order.marketOrder(
                    "AAPL",
                    OrderSide.SELL,
                    100L,
                    "TRADER1"
            );

            // Then
            assertThat(order).isNotNull();
            assertThat(order.type()).isEqualTo(OrderType.MARKET);
            assertThat(order.price()).isZero();
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTest {

        @Test
        @DisplayName("Should throw exception when symbol is null")
        void shouldThrowExceptionWhenSymbolIsNull() {
            assertThatThrownBy(() ->
                    Order.limitOrder(
                            null,
                            OrderSide.BUY,
                            150.0,
                            100L,
                            "TRADER1"
                    )
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Symbol cannot be null");
        }

        @ParameterizedTest
        @ValueSource(doubles = {-1.0, 0.0})
        @DisplayName("Should throw exception for invalid prices")
        void shouldThrowExceptionForInvalidPrices(double price) {
            assertThatThrownBy(() ->
                    Order.limitOrder(
                            "AAPL",
                            OrderSide.BUY,
                            price,
                            100L,
                            "TRADER1"
                    )
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Price must be positive");
        }

        @ParameterizedTest
        @ValueSource(longs = {-1L, 0L})
        @DisplayName("Should throw exception for invalid quantities")
        void shouldThrowExceptionForInvalidQuantities(long quantity) {
            assertThatThrownBy(() ->
                    Order.limitOrder(
                            "AAPL",
                            OrderSide.BUY,
                            150.0,
                            quantity,
                            "TRADER1"
                    )
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Quantity must be positive");
        }
    }

    @Nested
    @DisplayName("Utility Methods Tests")
    class UtilityMethodsTest {

        @Test
        @DisplayName("Should calculate remaining quantity correctly")
        void shouldCalculateRemainingQuantityCorrectly() {
            // Given
            Order order = Order.limitOrder("AAPL", OrderSide.BUY, 150.0, 100L, "TRADER1");

            // When
            Order partiallyFilled = order.withFilledQuantity(60L);

            // Then
            assertThat(partiallyFilled.getRemainingQuantity()).isEqualTo(40L);
        }

        @Test
        @DisplayName("Should update status correctly")
        void shouldUpdateStatusCorrectly() {
            // Given
            Order order = Order.limitOrder("AAPL", OrderSide.BUY, 150.0, 100L, "TRADER1");

            // When
            Order filledOrder = order.withStatus(OrderStatus.FILLED);

            // Then
            assertThat(filledOrder.status()).isEqualTo(OrderStatus.FILLED);
            assertThat(filledOrder.orderId()).isEqualTo(order.orderId());
        }

        @Test
        @DisplayName("Should detect complete orders")
        void shouldDetectCompleteOrders() {
            // Given
            Order order = Order.limitOrder("AAPL", OrderSide.BUY, 150.0, 100L, "TRADER1");

            // When
            Order filledOrder = order.withFilledQuantity(100L);

            // Then
            assertThat(filledOrder.isComplete()).isTrue();
            assertThat(order.isComplete()).isFalse();
        }
    }
}