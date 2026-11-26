package com.filemanager.dto;

import lombok.Data;

/** 任务状态与进度 */
@Data
public class DuplicateTaskDTO {

    private String id;

    /** RUNNING / DONE / FAILED / CANCELLED */
    private String status;

    /** SCAN_LEFT / SCAN_RIGHT / HASHING / GROUPING / DONE */
    private String phase;

    private String message;

    private String leftLabel;

    private String rightLabel;

    private Integer scannedLeft;

    private Integer scannedRight;

    /** 候选文件数(大小相同的桶里的文件) */
    private Integer candidateCount;

    private Integer hashedCount;

    /** 预计需要读取的字节数 */
    private Long totalBytes;

    /** 已经读取的字节数 */
    private Long readBytes;

    private Integer percent;

    /** 扫描/哈希过程中跳过的异常数 */
    private Integer warningCount;

    private String startedAt;

    private String finishedAt;

    private DuplicateSummaryDTO summary;
}
