package com.orange.logistics.streaming.ml;

import org.apache.spark.ml.Pipeline;
import org.apache.spark.ml.PipelineModel;
import org.apache.spark.ml.PipelineStage;
import org.apache.spark.ml.evaluation.RegressionEvaluator;
import org.apache.spark.ml.feature.StringIndexer;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.ml.regression.GBTRegressor;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import static org.apache.spark.sql.functions.*;

/**
 * 物流时效预测模型
 * 使用 Spark MLlib GBT 回归模型预测物流配送时间
 */
public class DeliveryTimePredictionModel {

    private static final String MODEL_PATH = "/tmp/models/delivery-time-prediction";
    private PipelineModel model;

    /**
     * 训练物流时效预测模型
     * 特征: 出发城市、目的城市、距离、包裹重量、当前状态
     */
    public void train(SparkSession spark, Dataset<Row> trainingData) {
        // 特征工程
        StringIndexer originCityIndexer = new StringIndexer()
                .setInputCol("originCity")
                .setOutputCol("originCityIndex")
                .setHandleInvalid("keep");

        StringIndexer destCityIndexer = new StringIndexer()
                .setInputCol("destinationCity")
                .setOutputCol("destCityIndex")
                .setHandleInvalid("keep");

        StringIndexer statusIndexer = new StringIndexer()
                .setInputCol("currentStatus")
                .setOutputCol("statusIndex")
                .setHandleInvalid("keep");

        VectorAssembler assembler = new VectorAssembler()
                .setInputCols(new String[]{
                        "originCityIndex", "destCityIndex", "statusIndex",
                        "distance", "weight", "transitStations", "hourOfDay", "dayOfWeek"
                })
                .setOutputCol("features");

        // GBT 回归模型
        GBTRegressor gbt = new GBTRegressor()
                .setLabelCol("deliveryHours")
                .setFeaturesCol("features")
                .setMaxIter(100)
                .setMaxDepth(5)
                .setStepSize(0.1);

        Pipeline pipeline = new Pipeline()
                .setStages(new PipelineStage[]{
                        originCityIndexer, destCityIndexer, statusIndexer, assembler, gbt
                });

        // 划分训练集和测试集
        Dataset<Row>[] splits = trainingData.randomSplit(new double[]{0.8, 0.2}, 42L);
        Dataset<Row> trainSet = splits[0];
        Dataset<Row> testSet = splits[1];

        // 训练模型
        model = pipeline.fit(trainSet);

        // 评估模型
        Dataset<Row> predictions = model.transform(testSet);
        RegressionEvaluator evaluator = new RegressionEvaluator()
                .setLabelCol("deliveryHours")
                .setPredictionCol("prediction")
                .setMetricName("rmse");

        double rmse = evaluator.evaluate(predictions);
        System.out.println("模型 RMSE: " + rmse);

        // 保存模型
        try {
            model.write().overwrite().save(MODEL_PATH);
            System.out.println("模型已保存至: " + MODEL_PATH);
        } catch (Exception e) {
            System.err.println("模型保存失败: " + e.getMessage());
        }
    }

    /**
     * 加载已训练的模型
     */
    public void loadModel() {
        try {
            model = PipelineModel.load(MODEL_PATH);
            System.out.println("模型加载成功");
        } catch (Exception e) {
            System.err.println("模型加载失败: " + e.getMessage());
        }
    }

    /**
     * 预测物流时效
     */
    public Dataset<Row> predict(Dataset<Row> inputData) {
        if (model == null) {
            throw new IllegalStateException("模型未加载，请先调用 train() 或 loadModel()");
        }

        return model.transform(inputData)
                .select("waybillNo", "orderId", "originCity", "destinationCity", "prediction")
                .withColumnRenamed("prediction", "predictedHours")
                .withColumn("confidence", lit(0.85))
                .withColumn("modelVersion", lit("v1.0"));
    }

    /**
     * 准备训练数据
     */
    public static Dataset<Row> prepareTrainingData(SparkSession spark, Dataset<Row> historicalData) {
        return historicalData
                .withColumn("hourOfDay", hour(col("eventTime")))
                .withColumn("dayOfWeek", dayofweek(col("eventTime")))
                .withColumn("distance",
                        sqrt(pow(col("destLatitude").minus(col("originLatitude")), 2)
                                .plus(pow(col("destLongitude").minus(col("originLongitude")), 2)))
                                .multiply(111)) // 粗略转换为公里
                .filter(col("deliveryHours").isNotNull())
                .filter(col("deliveryHours").gt(0).and(col("deliveryHours").lt(168))); // 过滤异常值
    }
}
