package com.filemanager.dto;

import lombok.Data;
import java.util.List;

@Data
public class TagGroupDTO {
    private Long id;
    private String name;
    private Integer sortOrder;
    private List<FileTagDTO> tags;
}