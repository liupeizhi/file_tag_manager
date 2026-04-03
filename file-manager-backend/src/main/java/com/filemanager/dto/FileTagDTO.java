package com.filemanager.dto;

import lombok.Data;
import java.util.List;

@Data
public class FileTagDTO {
    private Long id;
    private String name;
    private String color;
    private Long parentId;
    private Long groupId;
    private Integer sortOrder;
    private List<FileTagDTO> children;
}