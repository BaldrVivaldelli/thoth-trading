package com.aaa.thoth.engine;

import com.aaa.thoth.core.Order;
import com.aaa.thoth.core.Trade;
import com.aaa.thoth.core.enums.OrderStatus;
import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class TradingEngine {
    private static final Logger logger = LoggerFactory.getLogger(TradingEngine.class);
    private static final int RING_BUFFER_SIZE = 1024 * 64; // Debe ser potencia de 2

    private final AtomicBoolean isRunning;
    private final OrderValidator orderValidator;
    private final RiskManager riskManager;
    private final OrderBook orderBook;
    private final Disruptor<OrderEvent> disruptor;
    private final RingBuffer<OrderEvent> ringBuffer;
    private final ExecutorService executorService;
    private final ConcurrentMap<String, CompletableFuture<Order>> orderResults;

    public OrderBook.BookStatistics getBookStatistics(String symbol) {
        if (!isRunning.get()) {
            logger.warn("Trading Engine is not running");
            return null;
        }

        try {
            return orderBook.getStatistics(symbol);
        } catch (Exception e) {
            logger.error("Error getting book statistics for symbol {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    // Event para el Disruptor
    public static class OrderEvent {
        private Order order;
        private String correlationId;

        public void set(Order order, String correlationId) {
            this.order = order;
            this.correlationId = correlationId;
        }
    }

    public TradingEngine() {
        this.isRunning = new AtomicBoolean(false);
        this.orderValidator = new OrderValidator();
        this.riskManager = new RiskManager();
        this.orderBook = new OrderBook();
        this.orderResults = new ConcurrentHashMap<>();
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();

        // Configuración del Disruptor
        ThreadFactory threadFactory = Thread.ofVirtual()
                .name("trading-engine-", 0)
                .factory();

        this.disruptor = new Disruptor<>(
                OrderEvent::new,
                RING_BUFFER_SIZE,
                threadFactory,
                ProducerType.MULTI,
                new YieldingWaitStrategy()
        );

        // Configurar el pipeline de procesamiento
        this.disruptor.handleEventsWith(this::validateOrder)
                .then(this::checkRisk)
                .then(this::processOrder);

        this.ringBuffer = disruptor.getRingBuffer();
    }

    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            logger.info("Starting Trading Engine");
            disruptor.start();
        }
    }

    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            logger.info("Stopping Trading Engine");
            disruptor.shutdown();
            executorService.shutdown();
        }
    }

    public CompletableFuture<Order> submitOrder(Order order) {
        if (!isRunning.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Trading Engine is not running"));
        }

        CompletableFuture<Order> future = new CompletableFuture<>();
        orderResults.put(order.orderId(), future);

        // Publicar orden al ring buffer
        ringBuffer.publishEvent((event, sequence) ->
                event.set(order, order.orderId()));

        return future;
    }

    private void validateOrder(OrderEvent event, long sequence, boolean endOfBatch) {
        Order order = event.order;
        try {
            if (!orderValidator.validate(order)) {
                completeOrder(order.withStatus(OrderStatus.REJECTED));
            }
        } catch (Exception e) {
            logger.error("Error validating order: {}", order, e);
            completeOrder(order.withStatus(OrderStatus.REJECTED));
        }
    }

    private void checkRisk(OrderEvent event, long sequence, boolean endOfBatch) {
        Order order = event.order;
        try {
            if (!riskManager.checkRisk(order)) {
                completeOrder(order.withStatus(OrderStatus.REJECTED));
            }
        } catch (Exception e) {
            logger.error("Error in risk check for order: {}", order, e);
            completeOrder(order.withStatus(OrderStatus.REJECTED));
        }
    }

    private void processOrder(OrderEvent event, long sequence, boolean endOfBatch) {
        Order order = event.order;
        try {
            OrderBook.MatchingResult result = orderBook.processOrder(order);

            // Procesar trades resultantes
            result.trades().forEach(this::processTrade);

            // Actualizar orden con cantidad ejecutada
            if (result.remainingOrder() != null) {
                completeOrder(result.remainingOrder());
            } else {
                completeOrder(order.withStatus(OrderStatus.FILLED));
            }
        } catch (Exception e) {
            logger.error("Error processing order: {}", order, e);
            completeOrder(order.withStatus(OrderStatus.REJECTED));
        }
    }

    private void processTrade(Trade trade) {
        try {
            // Notificar el trade a los participantes
            notifyTradeParticipants(trade);

            // Publicar market data
            publishMarketData(trade);

            // Registrar el trade
            logTrade(trade);
        } catch (Exception e) {
            logger.error("Error processing trade: {}", trade, e);
        }
    }

    private void completeOrder(Order order) {
        CompletableFuture<Order> future = orderResults.remove(order.orderId());
        if (future != null) {
            future.complete(order);
        }
    }

    private void notifyTradeParticipants(Trade trade) {
        // Implementar notificación a participantes
    }

    private void publishMarketData(Trade trade) {
        // Implementar publicación de market data
    }

    private void logTrade(Trade trade) {
        // Implementar logging de trades
    }

    // Métodos para consultas y estadísticas
    public OrderBook.BookSnapshot getOrderBookSnapshot(String symbol) {
        return orderBook.getSnapshot(symbol);
    }
}