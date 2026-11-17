package com.filemanager.dto;

import lombok.Data;

@Data
public class AccessHistoryRequest {

    private Long serverId;
    private String path;
    private String name;
    private Boolean isDirectory;
}
