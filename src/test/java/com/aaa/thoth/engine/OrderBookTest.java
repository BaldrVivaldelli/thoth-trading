package com.aaa.thoth.engine;

import com.aaa.thoth.core.Order;
import com.aaa.thoth.core.Trade;
import com.aaa.thoth.core.enums.OrderSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("OrderBook Tests")
class OrderBookTest {

    private OrderBook orderBook;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook();
    }

    @Test
    @DisplayName("Should match market buy with limit sell")
    void shouldMatchMarketBuyWithLimitSell() {
        // Given
        Order sellOrder = Order.limitOrder(
                "AAPL",
                OrderSide.SELL,
                150.0,
                100L,
                "TRADER1"
        );

        // When - Add sell order to book
        OrderBook.MatchingResult sellResult = orderBook.processOrder(sellOrder);

        // Then - Verify sell order was added to book
        assertThat(sellResult.trades()).isEmpty();

        OrderBook.BookSnapshot initialSnapshot = orderBook.getSnapshot("AAPL");
        assertThat(initialSnapshot.asks())
                .hasSize(1)
                .first()
                .satisfies(level -> {
                    assertThat(level.price()).isEqualTo(150.0);
                    assertThat(level.quantity()).isEqualTo(100L);
                    assertThat(level.orderCount()).isEqualTo(1);
                });

        // When - Add market buy order
        Order buyOrder = Order.marketOrder(
                "AAPL",
                OrderSide.BUY,
                50L,
                "TRADER2"
        );
        OrderBook.MatchingResult buyResult = orderBook.processOrder(buyOrder);

        // Then - Verify the trade
        assertThat(buyResult.trades())
                .hasSize(1)
                .first()
                .satisfies(trade -> {
                    assertThat(trade.price()).isEqualTo(150.0);
                    assertThat(trade.quantity()).isEqualTo(50L);
                    assertThat(trade.makerOrder().orderId()).isEqualTo(sellOrder.orderId());
                    assertThat(trade.takerOrder().orderId()).isEqualTo(buyOrder.orderId());
                });

        // Verify the remaining sell order in book
        OrderBook.BookSnapshot finalSnapshot = orderBook.getSnapshot("AAPL");
        assertThat(finalSnapshot.asks())
                .hasSize(1)
                .first()
                .satisfies(level -> {
                    assertThat(level.price()).isEqualTo(150.0);
                    assertThat(level.quantity()).isEqualTo(50L);
                    assertThat(level.orderCount()).isEqualTo(1);
                });
    }

    @Test
    @DisplayName("Should maintain price-time priority")
    void shouldMaintainPriceTimePriority() {
        // Given
        Order sell1 = Order.limitOrder("AAPL", OrderSide.SELL, 150.0, 100L, "TRADER1");
        Order sell2 = Order.limitOrder("AAPL", OrderSide.SELL, 149.0, 100L, "TRADER2");
        Order buyMarket = Order.marketOrder("AAPL", OrderSide.BUY, 150L, "TRADER3");

        // When
        orderBook.processOrder(sell1);
        orderBook.processOrder(sell2);
        OrderBook.MatchingResult result = orderBook.processOrder(buyMarket);

        // Then
        assertThat(result.trades()).hasSize(2);
        assertThat(result.trades().get(0).price()).isEqualTo(149.0);
        assertThat(result.trades().get(1).price()).isEqualTo(150.0);
    }

    @Test
    @DisplayName("Should provide correct book snapshot")
    void shouldProvideCorrectBookSnapshot() {
        // Given
        Order sell1 = Order.limitOrder("AAPL", OrderSide.SELL, 150.0, 100L, "TRADER1");
        Order sell2 = Order.limitOrder("AAPL", OrderSide.SELL, 151.0, 100L, "TRADER2");

        // When
        orderBook.processOrder(sell1);
        orderBook.processOrder(sell2);
        OrderBook.BookSnapshot snapshot = orderBook.getSnapshot("AAPL");

        // Then
        assertThat(snapshot.asks()).hasSize(2);
        assertThat(snapshot.bids()).isEmpty();
        assertThat(snapshot.asks().get(0).price()).isEqualTo(150.0);
        assertThat(snapshot.asks().get(1).price()).isEqualTo(151.0);
    }
}