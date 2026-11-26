package com.filemanager.dto;

import lombok.Data;

@Data
public class DuplicateSummaryDTO {

    private Integer groupCount;

    /** 所有重复组里涉及的副本总数 */
    private Integer fileCount;

    /** 潜在可回收空间(字节) */
    private Long reclaimableBytes;

    /** 已复核确认的组数 */
    private Integer verifiedGroupCount;

    private Long leftFiles;

    private Long rightFiles;

    private Long durationMs;
}
