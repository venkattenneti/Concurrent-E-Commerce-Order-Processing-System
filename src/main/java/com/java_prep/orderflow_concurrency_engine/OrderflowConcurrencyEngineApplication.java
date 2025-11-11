package com.java_prep.orderflow_concurrency_engine;

import com.java_prep.orderflow_concurrency_engine.config.ApplicationConfig;
import com.java_prep.orderflow_concurrency_engine.model.Order;
import com.java_prep.orderflow_concurrency_engine.producer.OrderGenerator;
import com.java_prep.orderflow_concurrency_engine.producer.OrderProducer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@SpringBootApplication
public class OrderflowConcurrencyEngineApplication {

	private static final Logger logger = LoggerFactory.getLogger(OrderflowConcurrencyEngineApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(OrderflowConcurrencyEngineApplication.class, args);
	}
	@Bean
	public CommandLineRunner runTests() {
		return args -> {
			logger.info("========================================");
			logger.info("OrderFlow Concurrency Engine - Day 2 Test");
			logger.info("========================================");

			try {
				// Test 1: Basic functionality
				runBasicTest();

				Thread.sleep(2000);

				// Test 2: High rate
				runHighRateTest();

				Thread.sleep(2000);

				// Test 3: Flash sale
				runFlashSaleTest();

				logger.info("========================================");
				logger.info("All Day 2 tests completed successfully!");
				logger.info("========================================");

			} catch (Exception e) {
				logger.error("Test failed", e);
			}
		};
	}

	private static void runBasicTest() throws InterruptedException {
		logger.info("\n>>> TEST 1: Basic Order Generation (100 orders/sec)");

		ApplicationConfig config = new ApplicationConfig();
		BlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>(10000);
		OrderGenerator generator = new OrderGenerator(config);
		OrderProducer producer = new OrderProducer(orderQueue, generator, config);

		Thread producerThread = new Thread(producer, "producer-1");
		long startTime = System.currentTimeMillis();
		producerThread.start();

		logger.info("Running for 10 seconds...");
		Thread.sleep(10000);

		producer.stop();
		producerThread.join();
		long endTime = System.currentTimeMillis();

		long duration = endTime - startTime;
		long ordersProduced = producer.getOrdersProduced();
		double actualRate = (ordersProduced * 1000.0) / duration;
		int queueSize = orderQueue.size();

		logger.info("--- TEST 1 RESULTS ---");
		logger.info("Duration: {}ms", duration);
		logger.info("Orders Produced: {}", ordersProduced);
		logger.info("Target Rate: 100 orders/sec");
		logger.info("Actual Rate: {:.2f} orders/sec", actualRate);
		logger.info("Orders in Queue: {}", queueSize);
		logger.info("Expected: ~1000 orders (±50)");

		if (ordersProduced >= 950 && ordersProduced <= 1050) {
			logger.info("✓ TEST 1 PASSED");
		} else {
			logger.warn("✗ TEST 1 FAILED - Order count outside acceptable range");
		}

		logger.info("\n--- Sample Orders from Queue ---");
		for (int i = 0; i < 5 && !orderQueue.isEmpty(); i++) {
			Order order = orderQueue.poll();
			logger.info("Order {}: {} | Customer: {} ({}) | Items: {} | Total: ₹{}",
					i + 1,
					order.getOrderId(),
					order.getCustomerId(),
					order.getCustomerType(),
					order.getItems().size(),
					order.getTotalAmount());
		}
	}

	private static void runHighRateTest() throws InterruptedException {
		logger.info("\n>>> TEST 2: High Rate Generation (500 orders/sec)");

		ApplicationConfig config = new ApplicationConfig(500, 5, 20);
		BlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>(10000);
		OrderGenerator generator = new OrderGenerator(config);
		OrderProducer producer = new OrderProducer(orderQueue, generator, config);

		Thread producerThread = new Thread(producer, "producer-high-rate");
		long startTime = System.currentTimeMillis();
		producerThread.start();

		logger.info("Running for 5 seconds...");
		Thread.sleep(5000);

		producer.stop();
		producerThread.join();
		long endTime = System.currentTimeMillis();

		long duration = endTime - startTime;
		long ordersProduced = producer.getOrdersProduced();
		double actualRate = (ordersProduced * 1000.0) / duration;

		logger.info("--- TEST 2 RESULTS ---");
		logger.info("Duration: {}ms", duration);
		logger.info("Orders Produced: {}", ordersProduced);
		logger.info("Target Rate: 500 orders/sec");
		logger.info("Actual Rate: {:.2f} orders/sec", actualRate);
		logger.info("Expected: ~2500 orders (±125)");

		if (ordersProduced >= 2375 && ordersProduced <= 2625) {
			logger.info("✓ TEST 2 PASSED");
		} else {
			logger.warn("✗ TEST 2 FAILED - Order count outside acceptable range");
		}
	}

	private static void runFlashSaleTest() throws InterruptedException {
		logger.info("\n>>> TEST 3: Flash Sale Simulation (1000 orders/sec)");

		ApplicationConfig config = new ApplicationConfig(1000, 10, 30);
		BlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>(10000);
		OrderGenerator generator = new OrderGenerator(config);
		OrderProducer producer = new OrderProducer(orderQueue, generator, config);

		Thread producerThread = new Thread(producer, "producer-flash-sale");
		long startTime = System.currentTimeMillis();
		producerThread.start();

		logger.info("Running for 3 seconds...");
		Thread.sleep(3000);

		producer.stop();
		producerThread.join();
		long endTime = System.currentTimeMillis();

		long duration = endTime - startTime;
		long ordersProduced = producer.getOrdersProduced();
		double actualRate = (ordersProduced * 1000.0) / duration;

		logger.info("--- TEST 3 RESULTS ---");
		logger.info("Duration: {}ms", duration);
		logger.info("Orders Produced: {}", ordersProduced);
		logger.info("Target Rate: 1000 orders/sec");
		logger.info("Actual Rate: {:.2f} orders/sec", actualRate);
		logger.info("Expected: ~3000 orders (±150)");

		if (ordersProduced >= 2850 && ordersProduced <= 3150) {
			logger.info("✓ TEST 3 PASSED");
		} else {
			logger.warn("✗ TEST 3 FAILED - Order count outside acceptable range");
		}

		analyzeCustomerDistribution(orderQueue);
	}

	private static void analyzeCustomerDistribution(BlockingQueue<Order> queue) {
		int vipCount = 0, premiumCount = 0, regularCount = 0;
		int total = Math.min(queue.size(), 1000);

		logger.info("\n--- Customer Type Distribution (sample of {}) ---", total);

		for (int i = 0; i < total; i++) {
			Order order = queue.poll();
			if (order != null) {
				switch (order.getCustomerType()) {
					case VIP:
						vipCount++;
						break;
					case PREMIUM:
						premiumCount++;
						break;
					case REGULAR:
						regularCount++;
						break;
				}
			}
		}

		double vipPercent = (vipCount * 100.0) / total;
		double premiumPercent = (premiumCount * 100.0) / total;
		double regularPercent = (regularCount * 100.0) / total;

		logger.info("VIP: {} ({:.1f}%) - Expected: ~10%", vipCount, vipPercent);
		logger.info("PREMIUM: {} ({:.1f}%) - Expected: ~30%", premiumCount, premiumPercent);
		logger.info("REGULAR: {} ({:.1f}%) - Expected: ~60%", regularCount, regularPercent);
	}
}
