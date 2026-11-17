package com.filemanager.dto;

import lombok.Data;

@Data
public class FavoriteRequest {

    private Long serverId;
    private String path;
    private String name;
    private Boolean isDirectory;
}
