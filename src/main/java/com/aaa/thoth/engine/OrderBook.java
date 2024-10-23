package com.aaa.thoth.engine;

import com.aaa.thoth.core.Order;
import com.aaa.thoth.core.Trade;
import com.aaa.thoth.core.enums.OrderSide;
import com.aaa.thoth.core.enums.OrderType;
import com.aaa.thoth.core.enums.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.StampedLock;
import java.time.Instant;

public class OrderBook {
    private static final Logger logger = LoggerFactory.getLogger(OrderBook.class);
    private final ConcurrentHashMap<String, SymbolOrderBook> books;

    public OrderBook() {
        this.books = new ConcurrentHashMap<>();
    }

    public record MatchingResult(List<Trade> trades, Order remainingOrder) {
        @Override
        public String toString() {
            return String.format("MatchingResult{trades=%d, remainingOrder=%s}",
                    trades.size(),
                    remainingOrder != null ? remainingOrder.orderId() : "null");
        }
    }

    public record BookSnapshot(
            String symbol,
            List<PriceLevel> bids,
            List<PriceLevel> asks,
            double lastPrice,
            long lastQuantity
    ) {
        @Override
        public String toString() {
            return String.format("BookSnapshot{symbol=%s, bids=%d, asks=%d, lastPrice=%.2f, lastQty=%d}",
                    symbol, bids.size(), asks.size(), lastPrice, lastQuantity);
        }
    }

    public record BookStatistics(
            String symbol,
            int bidLevels,
            int askLevels,
            double bestBid,
            double bestAsk,
            double lastPrice,
            long lastQuantity
    ) {
        public double spread() {
            return bestAsk - bestBid;
        }

        public double midPrice() {
            return (bestBid + bestAsk) / 2.0;
        }
    }

    public record PriceLevel(
            double price,
            long quantity,
            int orderCount
    ) {
        @Override
        public String toString() {
            return String.format("PriceLevel{%.2f @ %d [%d orders]}",
                    price, quantity, orderCount);
        }
    }

    public MatchingResult processOrder(Order order) {
        logger.debug("Processing order: {}", order);
        return books.computeIfAbsent(order.symbol(), SymbolOrderBook::new)
                .processOrder(order);
    }

    public BookSnapshot getSnapshot(String symbol) {
        SymbolOrderBook book = books.get(symbol);
        return book != null ? book.getSnapshot() : null;
    }

    public BookStatistics getStatistics(String symbol) {
        SymbolOrderBook book = books.get(symbol);
        return book != null ? book.getStatistics() : null;
    }

    public void cancelOrder(String symbol, String orderId) {
        SymbolOrderBook book = books.get(symbol);
        if (book != null) {
            book.cancelOrder(orderId);
        }
    }

    private static class SymbolOrderBook {
        private final String symbol;
        private final StampedLock lock;
        private final ConcurrentSkipListMap<Double, OrderList> bids;
        private final ConcurrentSkipListMap<Double, OrderList> asks;
        private final Map<String, OrderInfo> ordersById;
        private volatile double lastPrice;
        private volatile long lastQuantity;

        private static class OrderList {
            final LinkedList<Order> orders = new LinkedList<>();
            long totalQuantity;

            void addOrder(Order order) {
                orders.addLast(order);
                totalQuantity += order.getRemainingQuantity();
            }

            Order removeFirst() {
                Order order = orders.removeFirst();
                totalQuantity -= order.getRemainingQuantity();
                return order;
            }

            void updateFirstOrder(Order updatedOrder, Order originalOrder) {
                orders.set(0, updatedOrder);
                totalQuantity = totalQuantity - originalOrder.getRemainingQuantity() + updatedOrder.getRemainingQuantity();
            }

            boolean isEmpty() {
                return orders.isEmpty();
            }

            int size() {
                return orders.size();
            }

            long getTotalQuantity() {
                return totalQuantity;
            }
        }

        private static class OrderInfo {
            final Order order;
            final double price;

            OrderInfo(Order order) {
                this.order = order;
                this.price = order.price();
            }
        }

        public SymbolOrderBook(String symbol) {
            this.symbol = symbol;
            this.lock = new StampedLock();
            this.bids = new ConcurrentSkipListMap<>(Comparator.reverseOrder());
            this.asks = new ConcurrentSkipListMap<>();
            this.ordersById = new HashMap<>();
        }

        public MatchingResult processOrder(Order order) {
            List<Trade> trades = new ArrayList<>();
            Order remainingOrder = order;

            long stamp = lock.writeLock();
            try {
                if (order.side() == OrderSide.BUY) {
                    remainingOrder = matchWithAsks(order, trades);
                } else {
                    remainingOrder = matchWithBids(order, trades);
                }

                if (remainingOrder != null &&
                        remainingOrder.getRemainingQuantity() > 0 &&
                        shouldAddToBook(remainingOrder)) {
                    addToBook(remainingOrder);
                }

                logger.debug("Order {} processed. Generated {} trades",
                        order.orderId(), trades.size());
                return new MatchingResult(trades, remainingOrder);
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        private Order matchWithAsks(Order buyOrder, List<Trade> trades) {
            Order currentOrder = buyOrder;

            while (currentOrder != null &&
                    currentOrder.getRemainingQuantity() > 0 &&
                    !asks.isEmpty()) {

                Map.Entry<Double, OrderList> bestAsk = asks.firstEntry();
                if (bestAsk == null || bestAsk.getValue().isEmpty()) {
                    break;
                }

                // Para órdenes límite, verificar el precio
                if (buyOrder.type() == OrderType.LIMIT &&
                        bestAsk.getKey() > buyOrder.price()) {
                    break;
                }

                OrderList orderList = bestAsk.getValue();
                Order sellOrder = orderList.orders.getFirst();

                long tradeQuantity = Math.min(
                        currentOrder.getRemainingQuantity(),
                        sellOrder.getRemainingQuantity()
                );

                Trade trade = Trade.createTrade(
                        sellOrder,
                        currentOrder,
                        bestAsk.getKey(),
                        tradeQuantity
                );

                trades.add(trade);
                logger.debug("Created trade: {}", trade);

                // Actualizar órdenes
                Order originalSellOrder = sellOrder;
                Order updatedSellOrder = sellOrder.withFilledQuantity(
                        sellOrder.filledQuantity() + tradeQuantity
                );

                currentOrder = currentOrder.withFilledQuantity(
                        currentOrder.filledQuantity() + tradeQuantity
                );

                // Actualizar el libro
                if (updatedSellOrder.isComplete()) {
                    orderList.removeFirst();
                    ordersById.remove(updatedSellOrder.orderId());
                    if (orderList.isEmpty()) {
                        asks.remove(bestAsk.getKey());
                    }
                } else {
                    orderList.updateFirstOrder(updatedSellOrder, originalSellOrder);
                    ordersById.put(updatedSellOrder.orderId(),
                            new OrderInfo(updatedSellOrder));
                }

                lastPrice = trade.price();
                lastQuantity = trade.quantity();
            }

            return currentOrder.getRemainingQuantity() > 0 ? currentOrder : null;
        }

        private Order matchWithBids(Order sellOrder, List<Trade> trades) {
            Order currentOrder = sellOrder;

            while (currentOrder != null &&
                    currentOrder.getRemainingQuantity() > 0 &&
                    !bids.isEmpty()) {

                Map.Entry<Double, OrderList> bestBid = bids.firstEntry();
                if (bestBid == null || bestBid.getValue().isEmpty()) {
                    break;
                }

                if (sellOrder.type() == OrderType.LIMIT &&
                        bestBid.getKey() < sellOrder.price()) {
                    break;
                }

                OrderList orderList = bestBid.getValue();
                Order buyOrder = orderList.orders.getFirst();

                long tradeQuantity = Math.min(
                        currentOrder.getRemainingQuantity(),
                        buyOrder.getRemainingQuantity()
                );

                Trade trade = Trade.createTrade(
                        buyOrder,
                        currentOrder,
                        bestBid.getKey(),
                        tradeQuantity
                );

                trades.add(trade);
                logger.debug("Created trade: {}", trade);

                Order updatedBuyOrder = buyOrder.withFilledQuantity(
                        buyOrder.filledQuantity() + tradeQuantity
                );

                currentOrder = currentOrder.withFilledQuantity(
                        currentOrder.filledQuantity() + tradeQuantity
                );

                if (updatedBuyOrder.isComplete()) {
                    orderList.removeFirst();
                    ordersById.remove(updatedBuyOrder.orderId());
                    if (orderList.isEmpty()) {
                        bids.remove(bestBid.getKey());
                    }
                } else {
                    orderList.orders.set(0, updatedBuyOrder);
                    ordersById.put(updatedBuyOrder.orderId(),
                            new OrderInfo(updatedBuyOrder));
                }

                lastPrice = trade.price();
                lastQuantity = trade.quantity();
            }

            return currentOrder.getRemainingQuantity() > 0 ? currentOrder : null;
        }

        private void addToBook(Order order) {
            if (order.type() == OrderType.MARKET) return;

            var priceMap = order.side() == OrderSide.BUY ? bids : asks;
            priceMap.computeIfAbsent(order.price(), k -> new OrderList())
                    .addOrder(order);

            ordersById.put(order.orderId(), new OrderInfo(order));
            logger.debug("Added order to book: {}", order);
        }

        private boolean shouldAddToBook(Order order) {
            return switch (order.type()) {
                case MARKET, IOC -> false;
                case FOK -> order.filledQuantity() == 0;
                default -> true;
            };
        }

        public void cancelOrder(String orderId) {
            long stamp = lock.writeLock();
            try {
                OrderInfo orderInfo = ordersById.remove(orderId);
                if (orderInfo != null) {
                    Order order = orderInfo.order;
                    var priceMap = order.side() == OrderSide.BUY ? bids : asks;
                    OrderList orderList = priceMap.get(orderInfo.price);
                    if (orderList != null) {
                        orderList.orders.remove(order);
                        if (orderList.isEmpty()) {
                            priceMap.remove(orderInfo.price);
                        }
                    }
                    logger.debug("Cancelled order: {}", orderId);
                }
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        public BookSnapshot getSnapshot() {
            long stamp = lock.tryOptimisticRead();
            try {
                List<PriceLevel> bidLevels = new ArrayList<>();
                bids.forEach((price, orderList) ->
                        bidLevels.add(new PriceLevel(price, orderList.totalQuantity,
                                orderList.orders.size())));

                List<PriceLevel> askLevels = new ArrayList<>();
                asks.forEach((price, orderList) ->
                        askLevels.add(new PriceLevel(price, orderList.totalQuantity,
                                orderList.orders.size())));

                return new BookSnapshot(symbol, bidLevels, askLevels,
                        lastPrice, lastQuantity);
            } finally {
                if (!lock.validate(stamp)) {
                    stamp = lock.readLock();
                    try {
                        return getSnapshot();
                    } finally {
                        lock.unlockRead(stamp);
                    }
                }
            }
        }

        public BookStatistics getStatistics() {
            long stamp = lock.tryOptimisticRead();
            try {
                double bestBid = bids.isEmpty() ? 0.0 : bids.firstKey();
                double bestAsk = asks.isEmpty() ? 0.0 : asks.firstKey();

                return new BookStatistics(
                        symbol,
                        bids.size(),
                        asks.size(),
                        bestBid,
                        bestAsk,
                        lastPrice,
                        lastQuantity
                );
            } finally {
                if (!lock.validate(stamp)) {
                    stamp = lock.readLock();
                    try {
                        return getStatistics();
                    } finally {
                        lock.unlockRead(stamp);
                    }
                }
            }
        }
    }
}