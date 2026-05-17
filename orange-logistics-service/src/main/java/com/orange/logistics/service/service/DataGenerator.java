package com.orange.logistics.service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.logistics.model.dto.TrackingEventDTO;
import com.orange.logistics.model.enums.LogisticsStatus;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 模拟数据生成器
 * 生成物流轨迹数据并发送到 Kafka
 */
public class DataGenerator {

    private static final Logger log = LoggerFactory.getLogger(DataGenerator.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    // 模拟城市和站点
    private static final String[][] CITIES = {
            {"北京", "BJ", "39.9042", "116.4074"},
            {"上海", "SH", "31.2304", "121.4737"},
            {"广州", "GZ", "23.1291", "113.2644"},
            {"深圳", "SZ", "22.5431", "114.0579"},
            {"杭州", "HZ", "30.2741", "120.1551"},
            {"成都", "CD", "30.5728", "104.0668"},
            {"武汉", "WH", "30.5928", "114.3055"},
            {"南京", "NJ", "32.0603", "118.7969"},
            {"西安", "XA", "34.3416", "108.9398"},
            {"重庆", "CQ", "29.4316", "106.9123"}
    };

    private static final String[] STATION_TYPES = {"揽收站", "分拣中心", "中转站", "配送站"};
    private static final String[] OPERATORS = {"张三", "李四", "王五", "赵六", "孙七", "周八"};

    private final KafkaProducer<String, String> producer;
    private final String topic;

    static {
        mapper.findAndRegisterModules();
    }

    public DataGenerator(String bootstrapServers, String topic) {
        this.topic = topic;

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10);

        this.producer = new KafkaProducer<>(props);
    }

    /**
     * 生成单条轨迹事件
     */
    public TrackingEventDTO generateEvent() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        String waybillNo = "OG" + String.format("%012d", random.nextLong(100000000000L));
        String orderId = "ORD" + String.format("%010d", random.nextLong(10000000000L));

        int cityIdx = random.nextInt(CITIES.length);
        String[] city = CITIES[cityIdx];

        LogisticsStatus status = LogisticsStatus.values()[random.nextInt(5)]; // 排除 EXCEPTION
        String stationType = STATION_TYPES[random.nextInt(STATION_TYPES.length)];
        String stationCode = city[1] + "-" + stationType.charAt(0) + random.nextInt(1, 10);

        return TrackingEventDTO.builder()
                .waybillNo(waybillNo)
                .orderId(orderId)
                .status(status)
                .stationCode(stationCode)
                .stationName(city[0] + stationType + random.nextInt(1, 5) + "号")
                .city(city[0])
                .province(city[0])
                .latitude(Double.parseDouble(city[2]) + random.nextDouble(-0.1, 0.1))
                .longitude(Double.parseDouble(city[3]) + random.nextDouble(-0.1, 0.1))
                .operator(OPERATORS[random.nextInt(OPERATORS.length)])
                .remark(generateRemark(status))
                .eventTime(LocalDateTime.now().minusMinutes(random.nextInt(0, 60)))
                .build();
    }

    /**
     * 生成异常事件（用于测试异常检测）
     */
    public TrackingEventDTO generateAnomalyEvent() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        TrackingEventDTO event = generateEvent();

        // 模拟超时：事件时间设为很久以前
        event.setEventTime(LocalDateTime.now().minusHours(random.nextInt(6, 24)));
        event.setRemark("模拟超时异常 - 包裹滞留");

        return event;
    }

    /**
     * 发送事件到 Kafka
     */
    public void sendEvent(TrackingEventDTO event) {
        try {
            String json = mapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.getWaybillNo(), json);
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("发送消息失败: {}", exception.getMessage());
                } else {
                    log.debug("消息发送成功: topic={}, partition={}, offset={}",
                            metadata.topic(), metadata.partition(), metadata.offset());
                }
            });
        } catch (Exception e) {
            log.error("序列化事件失败: {}", e.getMessage());
        }
    }

    /**
     * 批量生成并发送数据
     */
    public void generateBatch(int count, int anomalyPercent) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            TrackingEventDTO event;
            if (random.nextInt(100) < anomalyPercent) {
                event = generateAnomalyEvent();
            } else {
                event = generateEvent();
            }
            sendEvent(event);
        }

        producer.flush();
        log.info("批量发送 {} 条消息完成（异常比例: {}%）", count, anomalyPercent);
    }

    private String generateRemark(LogisticsStatus status) {
        return switch (status) {
            case PICKED_UP -> "快递员已揽收";
            case IN_TRANSIT -> "包裹运输中";
            case AT_TRANSFER -> "到达中转站，正在分拣";
            case OUT_FOR_DELIVERY -> "快递员正在派送";
            case DELIVERED -> "已签收，感谢使用Orange物流";
            case EXCEPTION -> "异常件，等待处理";
        };
    }

    public void close() {
        producer.close();
    }

    /**
     * 主方法 - 持续生成模拟数据
     */
    public static void main(String[] args) throws InterruptedException {
        String bootstrapServers = args.length > 0 ? args[0] : "localhost:9092";
        String topic = args.length > 1 ? args[1] : "logistics-tracking-events";
        int batchSize = args.length > 2 ? Integer.parseInt(args[2]) : 10;
        int intervalMs = args.length > 3 ? Integer.parseInt(args[3]) : 1000;
        int anomalyPercent = args.length > 4 ? Integer.parseInt(args[4]) : 10;

        log.info("启动数据生成器 servers={}, topic={}, batch={}, interval={}ms, anomaly={}%",
                bootstrapServers, topic, batchSize, intervalMs, anomalyPercent);

        DataGenerator generator = new DataGenerator(bootstrapServers, topic);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("关闭数据生成器...");
            generator.close();
        }));

        while (true) {
            generator.generateBatch(batchSize, anomalyPercent);
            Thread.sleep(intervalMs);
        }
    }
}
