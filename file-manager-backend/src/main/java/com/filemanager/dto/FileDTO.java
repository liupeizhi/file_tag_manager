package com.filemanager.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class FileDTO {
    private Long id;
    private Long serverId;
    private String path;
    private String fullPath;
    private String name;
    private Boolean isDirectory;
    private Long size;
    private String contentType;
    private LocalDateTime lastModified;
    private String fileType;
    private String icon;
    private String color;

    /** 子节点(仅目录树接口使用) */
    private List<FileDTO> children;

    /** 该分支是否因深度/数量限制被截断 */
    private Boolean truncated;
}