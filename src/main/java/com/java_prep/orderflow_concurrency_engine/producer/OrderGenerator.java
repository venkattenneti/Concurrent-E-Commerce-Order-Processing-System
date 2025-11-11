package com.java_prep.orderflow_concurrency_engine.producer;

import com.java_prep.orderflow_concurrency_engine.config.ApplicationConfig;
import com.java_prep.orderflow_concurrency_engine.model.CustomerType;
import com.java_prep.orderflow_concurrency_engine.model.Order;
import com.java_prep.orderflow_concurrency_engine.model.OrderItem;
import com.java_prep.orderflow_concurrency_engine.model.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class OrderGenerator {

    private static final Logger logger = LoggerFactory.getLogger(OrderGenerator.class);

    private final ApplicationConfig applicationConfig;
    private final Random random;
    private final AtomicLong orderCounter;
    private final AtomicLong customerCounter;

    private final List<Product> productCatalog;

    private static class Product{
        private final String productId;
        private final String productName;
        private final BigDecimal basePrice;
        private final String category;

        public Product(String productId, String productName, BigDecimal basePrice, String category) {
            this.productId = productId;
            this.productName = productName;
            this.basePrice = basePrice;
            this.category = category;
        }

        public String getProductId() {
            return productId;
        }

        public String getProductName() {
            return productName;
        }

        public BigDecimal getBasePrice() {
            return basePrice;
        }

        public String getCategory() {
            return category;
        }
    }

    public OrderGenerator(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
        this.random=new Random();
        this.orderCounter=new AtomicLong(0);
        this.customerCounter= new AtomicLong(0);
        this.productCatalog=initializeProductCatalog();

        logger.debug("Order Generator Intialized with {} products",productCatalog.size());
        logger.atDebug().setMessage("TotalProducts:{}").addArgument(productCatalog).log();
    }

    public Order generateOrder() throws Exception {
        try{
            String orderId= generateOrderId();
            String customerId= generateCustomerId();
            CustomerType customerType= selectCustomerType();
            List<OrderItem> items= generateOrderItems();
            BigDecimal totalAmount= calculateTotalAmount(items);
            int priority = getPriorityForCustomerType(customerType);
            LocalDateTime timeStamp= LocalDateTime.now();
            LocalDateTime expirationTime= timeStamp.plusMinutes(10);

            Order newOrder= new Order(orderId,
                    customerId,
                    customerType,timeStamp,
                    OrderStatus.PENDING,
                    items,
                    totalAmount,
                    priority,
                    expirationTime);

            logger.debug("Generated order: {} for customer: {} ({}), " +
                            "items: {}, total: ₹{}",
                    orderId, customerId, customerType,
                    items.size(), totalAmount);

            return newOrder;
        }catch (Exception e){
            logger.error("Error Generating Order: ",e);
            throw new Exception("Order Generation failed:",e);
        }
    }

    public String generateOrderId(){
        long orderId= orderCounter.incrementAndGet();
        String timeStamp= LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("ORD_%s_%06d",timeStamp,orderId);
    }

    private CustomerType selectCustomerType(){
        int rand =random.nextInt(100);

        if(rand<applicationConfig.getVipPercentage())
            return CustomerType.VIP;
        else if (rand< applicationConfig.getVipPercentage()+applicationConfig.getPremiumPercentage())
            return CustomerType.PREMIUM;
        else
            return CustomerType.REGULAR;
    }

    private String generateCustomerId(){
        if(random.nextInt(100)<70 && customerCounter.get()>1000){
            long existingId= 1000+random.nextInt((int)(customerCounter.get()-1000));
            return String.format("CUST_%05d",existingId);
        }else{
            long newId = customerCounter.incrementAndGet();
            return String.format("CUST_%05d",newId);
        }
    }

    private List<OrderItem> generateOrderItems(){
        int itemCount= 1+ random.nextInt(5);
        List<OrderItem> items= new ArrayList<>();

        for(int i=0;i<itemCount;i++){
            Product product=productCatalog.get(random.nextInt(productCatalog.size()));
            int quantity= 1+ random.nextInt(10);

            OrderItem item= new OrderItem(
                    product.getProductId(),
                    product.getProductName(),
                    quantity,
                    product.getBasePrice()
            );
            items.add(item);
        }
        return items;
    }

    private BigDecimal calculateTotalAmount(List<OrderItem> items){
        return items.stream()
                .map(item-> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO,BigDecimal::add);

    }

    private int getPriorityForCustomerType(CustomerType type){
        switch (type){
            case VIP : return 1;
            case PREMIUM : return 2;
            default : return 3;
        }
    }

    private List<Product> initializeProductCatalog() {
        List<Product> catalog = new ArrayList<>();

        // ELECTRONICS
        catalog.add(new Product("PROD_001", "iPhone 16 Pro", new BigDecimal("119900.00"), "Electronics"));
        catalog.add(new Product("PROD_002", "MacBook Pro M4", new BigDecimal("249900.00"), "Electronics"));
        catalog.add(new Product("PROD_003", "AirPods Pro", new BigDecimal("24900.00"), "Electronics"));
        catalog.add(new Product("PROD_004", "iPad Air", new BigDecimal("59900.00"), "Electronics"));
        catalog.add(new Product("PROD_005", "Apple Watch Series 10", new BigDecimal("45900.00"), "Electronics"));
        catalog.add(new Product("PROD_006", "Samsung Galaxy S24 Ultra", new BigDecimal("109900.00"), "Electronics"));
        catalog.add(new Product("PROD_007", "Sony WH-1000XM5 Headphones", new BigDecimal("29990.00"), "Electronics"));
        catalog.add(new Product("PROD_008", "Canon EOS R6 Camera", new BigDecimal("189900.00"), "Electronics"));

        // FASHION
        catalog.add(new Product("PROD_009", "Nike Air Max Shoes", new BigDecimal("8995.00"), "Fashion"));
        catalog.add(new Product("PROD_010", "Levi's 501 Original Jeans", new BigDecimal("3499.00"), "Fashion"));
        catalog.add(new Product("PROD_011", "Adidas Originals Hoodie", new BigDecimal("4999.00"), "Fashion"));
        catalog.add(new Product("PROD_012", "Ray-Ban Aviator Sunglasses", new BigDecimal("12000.00"), "Fashion"));
        catalog.add(new Product("PROD_013", "Fossil Leather Watch", new BigDecimal("9995.00"), "Fashion"));
        catalog.add(new Product("PROD_014", "Puma Running Track Pants", new BigDecimal("2499.00"), "Fashion"));

        // HOME & KITCHEN
        catalog.add(new Product("PROD_015", "Philips Air Fryer", new BigDecimal("8999.00"), "Home & Kitchen"));
        catalog.add(new Product("PROD_016", "Prestige Pressure Cooker 5L", new BigDecimal("2499.00"), "Home & Kitchen"));
        catalog.add(new Product("PROD_017", "Dyson V11 Vacuum Cleaner", new BigDecimal("45900.00"), "Home & Kitchen"));
        catalog.add(new Product("PROD_018", "Hamilton Beach Blender", new BigDecimal("3299.00"), "Home & Kitchen"));
        catalog.add(new Product("PROD_019", "Amazon Echo Dot 5th Gen", new BigDecimal("4999.00"), "Home & Kitchen"));

        // BOOKS
        catalog.add(new Product("PROD_020", "Atomic Habits by James Clear", new BigDecimal("599.00"), "Books"));
        catalog.add(new Product("PROD_021", "The Psychology of Money", new BigDecimal("399.00"), "Books"));
        catalog.add(new Product("PROD_022", "Sapiens by Yuval Noah Harari", new BigDecimal("699.00"), "Books"));

        // SPORTS & FITNESS
        catalog.add(new Product("PROD_023", "Yoga Mat Premium 6mm", new BigDecimal("1299.00"), "Sports & Fitness"));
        catalog.add(new Product("PROD_024", "Dumbbells Set 20kg", new BigDecimal("3999.00"), "Sports & Fitness"));
        catalog.add(new Product("PROD_025", "Fitbit Charge 6 Tracker", new BigDecimal("14999.00"), "Sports & Fitness"));

        logger.info("Initialized product catalog with {} products across {} categories",
                catalog.size(),
                catalog.stream().map(Product::getCategory).distinct().count());

        return catalog;
    }
}
