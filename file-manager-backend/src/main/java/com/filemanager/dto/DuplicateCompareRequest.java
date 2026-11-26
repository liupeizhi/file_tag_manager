package com.filemanager.dto;

import lombok.Data;

/**
 * 重复文件检测的入参。
 *
 * 只做「两个目录」的比对, 不支持整服务器对整服务器。
 */
@Data
public class DuplicateCompareRequest {

    private Long leftServerId;

    private Long rightServerId;

    private String leftPath;

    private String rightPath;

    /** 忽略小于该字节数的文件; null 视为 0 */
    private Long minSize;

    /** 忽略隐藏文件与系统垃圾文件; null 视为 true */
    private Boolean ignoreHidden;

    /** 严格模式: 对所有候选做全量哈希(L3); null 视为 false(L2 采样哈希) */
    private Boolean strict;
}
