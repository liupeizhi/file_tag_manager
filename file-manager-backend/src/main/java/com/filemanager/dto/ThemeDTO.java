package com.filemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThemeDTO {

    private Integer fontSize;
    private String brightness;
    private String style;
    private String textColor;
    private String accentColor;
    private BackgroundDTO background;
    private Long updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackgroundDTO {
        private String name;
        private String mime;
        private Long updatedAt;
    }
}
