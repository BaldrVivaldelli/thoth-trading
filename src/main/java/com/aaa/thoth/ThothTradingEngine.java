package com.aaa.thoth;

import com.aaa.thoth.core.Order;
import com.aaa.thoth.core.enums.OrderSide;
import com.aaa.thoth.core.enums.OrderType;
import com.aaa.thoth.engine.OrderBook;
import com.aaa.thoth.engine.TradingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ThothTradingEngine {
    private static final Logger logger = LoggerFactory.getLogger(ThothTradingEngine.class);

    public static void main(String[] args) {
        logger.info("Iniciando Thoth Trading Engine...");

        // Inicializar el motor de trading
        TradingEngine tradingEngine = new TradingEngine();
        tradingEngine.start();

        // Crear algunas órdenes de ejemplo
        try {
            demoTrading(tradingEngine);
        } catch (Exception e) {
            logger.error("Error en la demostración: ", e);
        }

        // Iniciar interfaz de consola simple
        runConsoleInterface(tradingEngine);

        // Apagar el motor al terminar
        tradingEngine.stop();
        logger.info("Thoth Trading Engine finalizado.");
    }

    private static void demoTrading(TradingEngine tradingEngine) throws ExecutionException, InterruptedException {
        // Crear orden de compra usando el orderId generado
        Order buyOrder = Order.limitOrder(
                "AAPL",         // symbol
                OrderSide.BUY,  // side
                150.0,          // price
                100,            // quantity
                "TRADER1"       // traderId
        );

        // Crear orden de venta usando el orderId generado
        Order sellOrder = Order.limitOrder(
                "AAPL",         // symbol
                OrderSide.SELL, // side
                150.0,          // price
                50,             // quantity
                "TRADER2"       // traderId
        );

        logger.info("Enviando orden de compra: {}", buyOrder);
        CompletableFuture<Order> buyResult = tradingEngine.submitOrder(buyOrder);

        logger.info("Enviando orden de venta: {}", sellOrder);
        CompletableFuture<Order> sellResult = tradingEngine.submitOrder(sellOrder);

        // Esperar y mostrar resultados
        Order processedBuyOrder = buyResult.join();
        Order processedSellOrder = sellResult.join();

        logger.info("Resultado orden de compra: {}", processedBuyOrder);
        logger.info("Resultado orden de venta: {}", processedSellOrder);

        // Mostrar el libro de órdenes
        OrderBook.BookSnapshot snapshot = tradingEngine.getOrderBookSnapshot("AAPL");
        logger.info("Estado del libro de órdenes AAPL: {}", snapshot);
    }

    private static void runConsoleInterface(TradingEngine tradingEngine) {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            System.out.println("\nThoth Trading Engine - Menú Principal");
            System.out.println("1. Crear orden de compra");
            System.out.println("2. Crear orden de venta");
            System.out.println("3. Ver libro de órdenes");
            System.out.println("4. Ver estadísticas");
            System.out.println("5. Salir");
            System.out.print("Seleccione una opción: ");

            String option = scanner.nextLine();

            try {
                switch (option) {
                    case "1" -> createOrder(tradingEngine, OrderSide.BUY, scanner);
                    case "2" -> createOrder(tradingEngine, OrderSide.SELL, scanner);
                    case "3" -> showOrderBook(tradingEngine, scanner);
                    case "4" -> showStatistics(tradingEngine, scanner);
                    case "5" -> running = false;
                    default -> System.out.println("Opción no válida");
                }
            } catch (Exception e) {
                logger.error("Error procesando la opción: ", e);
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private static void createOrder(TradingEngine tradingEngine, OrderSide side, Scanner scanner)
            throws ExecutionException, InterruptedException {
        System.out.print("Símbolo (ej. AAPL): ");
        String symbol = scanner.nextLine().toUpperCase();

        System.out.print("Precio: ");
        double price = Double.parseDouble(scanner.nextLine());

        System.out.print("Cantidad: ");
        long quantity = Long.parseLong(scanner.nextLine());

        System.out.print("ID del trader: ");
        String traderId = scanner.nextLine();

        Order order = Order.limitOrder(
                symbol,
                side,
                price,
                quantity,
                traderId
        );

        CompletableFuture<Order> result = tradingEngine.submitOrder(order);
        Order processedOrder = result.get();

        System.out.println("Orden procesada: " + processedOrder);
    }

    private static void showOrderBook(TradingEngine tradingEngine, Scanner scanner) {
        System.out.print("Símbolo (ej. AAPL): ");
        String symbol = scanner.nextLine().toUpperCase();

        OrderBook.BookSnapshot snapshot = tradingEngine.getOrderBookSnapshot(symbol);
        if (snapshot != null) {
            System.out.println("\nLibro de Órdenes para " + symbol);
            System.out.println("Compras (Bids):");
            snapshot.bids().forEach(level ->
                    System.out.printf("  Precio: %.2f, Cantidad: %d, Órdenes: %d%n",
                            level.price(), level.quantity(), level.orderCount()));

            System.out.println("Ventas (Asks):");
            snapshot.asks().forEach(level ->
                    System.out.printf("  Precio: %.2f, Cantidad: %d, Órdenes: %d%n",
                            level.price(), level.quantity(), level.orderCount()));
        } else {
            System.out.println("No hay libro de órdenes para " + symbol);
        }
    }

    private static void showStatistics(TradingEngine tradingEngine, Scanner scanner) {
        System.out.print("Símbolo (ej. AAPL): ");
        String symbol = scanner.nextLine().toUpperCase();

        OrderBook.BookStatistics stats = tradingEngine.getBookStatistics(symbol);
        if (stats != null) {
            System.out.println("\nEstadísticas para " + symbol);
            System.out.printf("Mejor compra: %.2f%n", stats.bestBid());
            System.out.printf("Mejor venta: %.2f%n", stats.bestAsk());
            System.out.printf("Spread: %.2f%n", stats.spread());
            System.out.printf("Precio medio: %.2f%n", stats.midPrice());
            System.out.printf("Último precio: %.2f%n", stats.lastPrice());
            System.out.printf("Última cantidad: %d%n", stats.lastQuantity());
            System.out.printf("Niveles de compra: %d%n", stats.bidLevels());
            System.out.printf("Niveles de venta: %d%n", stats.askLevels());
        } else {
            System.out.println("No hay estadísticas para " + symbol);
        }
    }
}
