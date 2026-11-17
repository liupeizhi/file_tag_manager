package com.filemanager.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FavoriteDTO {

    private Long id;
    private Long serverId;
    private String serverName;
    private String path;
    private String name;
    private Boolean isDirectory;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
