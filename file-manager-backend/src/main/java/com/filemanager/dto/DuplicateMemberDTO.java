package com.filemanager.dto;

import lombok.Data;

/** 重复组里的一个副本 */
@Data
public class DuplicateMemberDTO {

    /** LEFT / RIGHT */
    private String side;

    /** 相对各自根目录的路径, 用于对照 */
    private String relativePath;

    /** 完整路径 */
    private String path;

    private String name;

    private Long size;

    private String lastModified;

    private Long serverId;
}
