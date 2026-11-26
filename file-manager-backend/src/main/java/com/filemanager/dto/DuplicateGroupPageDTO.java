package com.filemanager.dto;

import lombok.Data;

import java.util.List;

@Data
public class DuplicateGroupPageDTO {

    private Integer total;

    private Integer page;

    private Integer size;

    private List<DuplicateGroupDTO> items;
}
