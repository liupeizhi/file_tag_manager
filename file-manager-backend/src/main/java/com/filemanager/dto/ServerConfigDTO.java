package com.filemanager.dto;

import lombok.Data;

@Data
public class ServerConfigDTO {
    private Long id;
    private String name;
    private String protocol;
    private String url;
    private String username;
    private String password;
    private String rootPath;
    private Boolean enabled;
    private String description;
    private String extraConfig;
}