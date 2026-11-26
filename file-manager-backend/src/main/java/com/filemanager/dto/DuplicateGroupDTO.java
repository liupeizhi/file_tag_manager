package com.filemanager.dto;

import lombok.Data;

import java.util.List;

/** 一「组」内容相同的文件 */
@Data
public class DuplicateGroupDTO {

    /** 用指纹当 id, 同一批文件重复扫描时保持不变 */
    private String id;

    private Long size;

    /** 保留一份可以回收的空间 = size * (成员数 - 1) */
    private Long reclaimableBytes;

    /** 左右两边都有成员 */
    private Boolean cross;

    /** 是否已用全量哈希复核过 */
    private Boolean verified;

    /** 复核后发现内容其实不同(L2 误判) */
    private Boolean mismatch;

    private String message;

    private List<DuplicateMemberDTO> members;
}
