package com.orange.logistics.scheduler.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 任务执行日志
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("task_log")
public class TaskLog {

    @Id
    private Long id;

    /**
     * 任务名称
     */
    private String jobName;

    /**
     * 执行状态：SUCCESS / FAILED / RUNNING
     */
    private String status;

    /**
     * 执行开始时     */
    private LocalDateTime startTime;

    /**
     * 执行结束时间
     */
    private LocalDateTime endTime;

    /**
     * 执行耗时（毫秒）
     */
    private Long duration;

    /**
     * 处理数据条数
     */
    private Integer processedCount;

    /**
     * 执行结果描述
     */
    private String resultMessage;

    /**
     * 异常信息
     */
    private String errorMessage;
}
