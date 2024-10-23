package com.aaa.thoth.core;

import com.aaa.thoth.core.enums.OrderSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Trade Tests")
class TradeTest {

    private Order buyOrder;
    private Order sellOrder;

    @BeforeEach
    void setUp() {
        buyOrder = Order.limitOrder(
                "AAPL",
                OrderSide.BUY,
                150.0,
                100L,
                "TRADER1"
        );

        sellOrder = Order.limitOrder(
                "AAPL",
                OrderSide.SELL,
                150.0,
                100L,
                "TRADER2"
        );
    }

    @Nested
    @DisplayName("Trade Creation Tests")
    class TradeCreationTest {

        @Test
        @DisplayName("Should create valid trade")
        void shouldCreateValidTrade() {
            // When
            Trade trade = Trade.createTrade(buyOrder, sellOrder, 150.0, 50L);

            // Then
            assertThat(trade).isNotNull();
            assertThat(trade.symbol()).isEqualTo("AAPL");
            assertThat(trade.price()).isEqualTo(150.0);
            assertThat(trade.quantity()).isEqualTo(50L);
            assertThat(trade.tradeId()).isNotNull().isNotBlank();
            assertThat(trade.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("Should calculate total correctly")
        void shouldCalculateTotalCorrectly() {
            // When
            Trade trade = Trade.createTrade(buyOrder, sellOrder, 150.0, 50L);

            // Then
            assertThat(trade.getTotal()).isEqualTo(7500.0); // 150.0 * 50
        }
    }

    @Nested
    @DisplayName("Trade Validation Tests")
    class TradeValidationTest {

        @Test
        @DisplayName("Should throw exception for different symbols")
        void shouldThrowExceptionForDifferentSymbols() {
            // Given
            Order differentSymbolOrder = Order.limitOrder(
                    "GOOGL",
                    OrderSide.SELL,
                    150.0,
                    100L,
                    "TRADER2"
            );

            // Then
            assertThatThrownBy(() ->
                    Trade.createTrade(buyOrder, differentSymbolOrder, 150.0, 50L)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("same symbol");
        }

        @Test
        @DisplayName("Should throw exception for same side orders")
        void shouldThrowExceptionForSameSideOrders() {
            // Given
            Order anotherBuyOrder = Order.limitOrder(
                    "AAPL",
                    OrderSide.BUY,
                    150.0,
                    100L,
                    "TRADER2"
            );

            // Then
            assertThatThrownBy(() ->
                    Trade.createTrade(buyOrder, anotherBuyOrder, 150.0, 50L)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("opposite sides");
        }

        @Test
        @DisplayName("Should throw exception for invalid quantity")
        void shouldThrowExceptionForInvalidQuantity() {
            assertThatThrownBy(() ->
                    Trade.createTrade(buyOrder, sellOrder, 150.0, 0L)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("quantity must be positive");
        }
    }

    @Nested
    @DisplayName("Trade Utility Methods Tests")
    class TradeUtilityMethodsTest {

        @Test
        @DisplayName("Should identify buyer and seller correctly")
        void shouldIdentifyBuyerAndSellerCorrectly() {
            // When
            Trade trade = Trade.createTrade(buyOrder, sellOrder, 150.0, 50L);

            // Then
            assertThat(trade.getBuyOrder()).isEqualTo(buyOrder);
            assertThat(trade.getSellOrder()).isEqualTo(sellOrder);
            assertThat(trade.getBuyTraderId()).isEqualTo("TRADER1");
            assertThat(trade.getSellTraderId()).isEqualTo("TRADER2");
        }

        @Test
        @DisplayName("Should handle exchange ID correctly")
        void shouldHandleExchangeIdCorrectly() {
            // Given
            Trade trade = Trade.createTrade(buyOrder, sellOrder, 150.0, 50L);

            // When
            Trade tradeWithExchangeId = trade.withExchangeId("EX123");

            // Then
            assertThat(tradeWithExchangeId.exchangeId())
                    .isEqualTo("EX123");
            assertThat(tradeWithExchangeId.tradeId())
                    .isEqualTo(trade.tradeId());
        }
    }
}