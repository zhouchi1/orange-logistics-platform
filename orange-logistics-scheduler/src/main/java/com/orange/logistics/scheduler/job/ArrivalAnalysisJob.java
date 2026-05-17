package com.orange.logistics.scheduler.job;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

/**
 * 到货时间分析定时任务
 * 通过 XXL-JOB 调度，调用 AI 预测服务执行每日分析
 */
@Slf4j
@Component
public class ArrivalAnalysisJob {

    private final WebClient webClient;

    public ArrivalAnalysisJob(@Value("${ai.prediction.url:http://localhost:8001}") String aiUrl) {
        this.webClient = WebClient.builder().baseUrl(aiUrl).build();
    }

    /**
     * 每日全量分析任务
     * 分析所有门店的最优到货时间，结果写入数据库
     * 建议调度时间：每天凌晨 4:00
     */
    @XxlJob("arrivalDailyAnalysisHandler")
    public void dailyAnalysis() {
        String targetDate = LocalDate.now().plusDays(1).toString();
        XxlJobHelper.log("【到货分析任务】开始执行，目标日期: {}", targetDate);
        log.info("【到货分析任务】开始执行，目标日期: {}", targetDate);

        try {
            Map<String, Object> body = Map.of("target_date", targetDate);

            Map result = webClient.post()
                    .uri("/api/v1/arrival/job/daily-analysis")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMinutes(5))
                    .block();

            if (result != null) {
                Map summary = (Map) result.get("summary");
                XxlJobHelper.log("分析完成: 门店数={}, 已分析={}, 平均置信度={}",
                        summary.get("total_stores"),
                        summary.get("analyzed"),
                        summary.get("avg_confidence"));
                log.info("【到货分析任务】完成: {}", summary);
            }
        } catch (Exception e) {
            log.error("【到货分析任务】执行失败", e);
            XxlJobHelper.handleFail("执行失败: " + e.getMessage());
        }
    }

    /**
     * 构建到货-收益关联数据
     * 将配送记录和销售数据关联，用于模型训练
     * 建议调度时间：每天凌晨 5:00
     */
    @XxlJob("arrivalCorrelationBuildHandler")
    public void buildCorrelation() {
        XxlJobHelper.log("【关联数据构建】开始执行");
        log.info("【关联数据构建】开始执行");

        try {
            Map<String, Object> body = Map.of("days", 7);

            Map result = webClient.post()
                    .uri("/api/v1/arrival/job/build-correlation")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMinutes(10))
                    .block();

            if (result != null) {
                Map data = (Map) result.get("result");
                XxlJobHelper.log("构建完成: 记录数={}, 处理门店={}",
                        data.get("total_records"), data.get("stores_processed"));
                log.info("【关联数据构建】完成: {}", data);
            }
        } catch (Exception e) {
            log.error("【关联数据构建】执行失败", e);
            XxlJobHelper.handleFail("执行失败: " + e.getMessage());
        }
    }

    /**
     * 模型重训练触发
     * 每周一触发门店收益模型重训练
     * 建议调度时间：每周一凌晨 3:00
     */
    @XxlJob("arrivalModelRetrainHandler")
    public void triggerRetrain() {
        XxlJobHelper.log("【模型重训练】开始触发");
        log.info("【模型重训练】开始触发");

        try {
            Map<String, Object> body = Map.of(
                    "model_type", "store_revenue",
                    "params", Map.of("epochs", 100, "learning_rate", 0.001)
            );

            Map result = webClient.post()
                    .uri("/api/v1/train/trigger")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMinutes(1))
                    .block();

            if (result != null) {
                XxlJobHelper.log("训练任务已提交: task_id={}, status={}",
                        result.get("task_id"), result.get("status"));
                log.info("【模型重训练】任务已提交: {}", result);
            }
        } catch (Exception e) {
            log.error("【模型重训练】触发失败", e);
            XxlJobHelper.handleFail("触发失败: " + e.getMessage());
        }
    }
}
