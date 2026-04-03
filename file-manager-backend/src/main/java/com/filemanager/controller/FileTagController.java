package com.filemanager.controller;

import com.filemanager.dto.ApiResponse;
import com.filemanager.dto.FileDTO;
import com.filemanager.dto.FileTagDTO;
import com.filemanager.dto.TagGroupDTO;
import com.filemanager.service.FileTagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/tags")
public class FileTagController {
    
    @Autowired
    private FileTagService tagService;
    
    @GetMapping("/tree")
    public ApiResponse<List<FileTagDTO>> getTagTree() {
        return ApiResponse.success(tagService.getTagTree());
    }
    
    @GetMapping("/groups")
    public ApiResponse<List<TagGroupDTO>> getTagGroups() {
        return ApiResponse.success(tagService.getTagGroupsWithTags());
    }
    
    @PostMapping("/groups")
    public ApiResponse<TagGroupDTO> createTagGroup(@RequestBody TagGroupDTO dto) {
        return ApiResponse.success(tagService.createTagGroup(dto));
    }
    
    @PutMapping("/groups/{id}")
    public ApiResponse<TagGroupDTO> updateTagGroup(@PathVariable Long id, @RequestBody TagGroupDTO dto) {
        return ApiResponse.success(tagService.updateTagGroup(id, dto));
    }
    
    @DeleteMapping("/groups/{id}")
    public ApiResponse<Void> deleteTagGroup(@PathVariable Long id) {
        tagService.deleteTagGroup(id);
        return ApiResponse.success("删除成功", null);
    }
    
    @PostMapping
    public ApiResponse<FileTagDTO> createTag(@RequestBody FileTagDTO dto) {
        return ApiResponse.success(tagService.createTag(dto));
    }
    
    @PutMapping("/{id}")
    public ApiResponse<FileTagDTO> updateTag(@PathVariable Long id, @RequestBody FileTagDTO dto) {
        return ApiResponse.success(tagService.updateTag(id, dto));
    }
    
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return ApiResponse.success("删除成功", null);
    }
    
    @PostMapping("/file")
    public ApiResponse<Void> addTagToFile(
            @RequestParam String filePath,
            @RequestParam Long serverId,
            @RequestParam Long tagId) {
        tagService.addTagToFile(filePath, serverId, tagId);
        return ApiResponse.success("添加成功", null);
    }
    
    @DeleteMapping("/file")
    public ApiResponse<Void> removeTagFromFile(
            @RequestParam String filePath,
            @RequestParam Long serverId,
            @RequestParam Long tagId) {
        tagService.removeTagFromFile(filePath, serverId, tagId);
        return ApiResponse.success("移除成功", null);
    }
    
    @PostMapping("/file/batch")
    public ApiResponse<Void> setFileTags(
            @RequestParam String filePath,
            @RequestParam Long serverId,
            @RequestBody List<Long> tagIds) {
        tagService.setFileTags(filePath, serverId, tagIds);
        return ApiResponse.success("设置成功", null);
    }
    
    @GetMapping("/file")
    public ApiResponse<List<FileTagDTO>> getFileTags(
            @RequestParam String filePath,
            @RequestParam Long serverId) {
        return ApiResponse.success(tagService.getFileTags(filePath, serverId));
    }
    
    @GetMapping("/{tagId}/files")
    public ApiResponse<List<String>> getFilesByTag(@PathVariable Long tagId) {
        return ApiResponse.success(tagService.getFilesByTag(tagId));
    }
    
    @GetMapping("/{tagId}/files/detail")
    public ApiResponse<List<FileDTO>> getFilesByTagWithDetails(@PathVariable Long tagId) {
        return ApiResponse.success(tagService.getFilesByTagWithDetails(tagId));
    }
}