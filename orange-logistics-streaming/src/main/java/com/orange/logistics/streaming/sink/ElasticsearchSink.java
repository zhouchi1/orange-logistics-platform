package com.orange.logistics.streaming.sink;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.HashMap;
import java.util.Map;

/**
 * Elasticsearch Sink
 * 将轨迹数据写入 Elasticsearch 用于检索和聚合分析
 */
public class ElasticsearchSink {

    private static final String ES_NODES = System.getProperty("es.nodes", "elasticsearch:9200");
    private static final String ES_INDEX_TRACKING = "logistics-tracking";

    /**
     * 批量写入轨迹数据到 Elasticsearch
     */
    public static void writeBatch(Dataset<Row> batchDF, long batchId) {
        Map<String, String> esConfig = new HashMap<>();
        esConfig.put("es.nodes", ES_NODES);
        esConfig.put("es.port", "9200");
        esConfig.put("es.index.auto.create", "true");
        esConfig.put("es.nodes.wan.only", "true");
        esConfig.put("es.batch.size.entries", "1000");
        esConfig.put("es.batch.write.retry.count", "3");

        // 写入 Elasticsearch
        batchDF.selectExpr(
                        "waybillNo", "orderId", "status", "stationCode", "stationName",
                        "city", "province", "latitude", "longitude", "operator",
                        "remark", "CAST(eventTime AS STRING) as eventTime"
                )
                .write()
                .format("org.elasticsearch.spark.sql")
                .options(esConfig)
                .mode("append")
                .save(ES_INDEX_TRACKING);

        System.out.println("[ES Sink] Batch " + batchId + " 写入 " + batchDF.count() + " 条轨迹记录");
    }
}
