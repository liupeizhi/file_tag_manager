package com.filemanager.service;

import com.filemanager.dto.FileDTO;
import com.filemanager.dto.FileTagDTO;
import com.filemanager.dto.TagGroupDTO;
import com.filemanager.entity.FileMetadata;
import com.filemanager.entity.FileTag;
import com.filemanager.entity.FileTagRelation;
import com.filemanager.entity.ServerConfig;
import com.filemanager.entity.TagGroup;
import com.filemanager.repository.FileMetadataRepository;
import com.filemanager.repository.FileTagRepository;
import com.filemanager.repository.FileTagRelationRepository;
import com.filemanager.repository.ServerConfigRepository;
import com.filemanager.repository.TagGroupRepository;
import com.github.sardine.DavResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FileTagService {
    
    @Autowired
    private FileTagRepository tagRepository;
    
    @Autowired
    private FileTagRelationRepository relationRepository;
    
    @Autowired
    private ServerConfigRepository serverConfigRepository;
    
    @Autowired
    private WebDavService webDavService;
    
    @Autowired
    private TagGroupRepository tagGroupRepository;
    
    @Autowired
    private FileMetadataRepository fileMetadataRepository;
    
    @Autowired
    private FileService fileService;
    
    public List<FileTagDTO> getTagTree() {
        List<FileTag> allTags = tagRepository.findAllByOrderBySortOrderAsc();
        return buildTagTree(allTags, null, null);
    }
    
    public List<FileTagDTO> getTagTreeByGroup(Long groupId) {
        List<FileTag> allTags = tagRepository.findByGroupIdOrderBySortOrderAsc(groupId);
        return buildTagTree(allTags, null, groupId);
    }
    
    public List<TagGroupDTO> getTagGroupsWithTags() {
        List<TagGroup> groups = tagGroupRepository.findAllByOrderBySortOrderAsc();
        return groups.stream().map(group -> {
            TagGroupDTO dto = new TagGroupDTO();
            dto.setId(group.getId());
            dto.setName(group.getName());
            dto.setSortOrder(group.getSortOrder());
            dto.setTags(getTagTreeByGroup(group.getId()));
            return dto;
        }).collect(Collectors.toList());
    }
    
    @Transactional
    public TagGroupDTO createTagGroup(TagGroupDTO dto) {
        TagGroup group = new TagGroup();
        group.setName(dto.getName());
        group.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        TagGroup saved = tagGroupRepository.save(group);
        return toGroupDTO(saved);
    }
    
    @Transactional
    public TagGroupDTO updateTagGroup(Long id, TagGroupDTO dto) {
        TagGroup group = tagGroupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("标签分组不存在"));
        if (dto.getName() != null) group.setName(dto.getName());
        if (dto.getSortOrder() != null) group.setSortOrder(dto.getSortOrder());
        TagGroup saved = tagGroupRepository.save(group);
        return toGroupDTO(saved);
    }
    
    @Transactional
    public void deleteTagGroup(Long id) {
        List<FileTag> tags = tagRepository.findByGroupIdOrderBySortOrderAsc(id);
        for (FileTag tag : tags) {
            relationRepository.deleteByTagId(tag.getId());
        }
        tagRepository.deleteAll(tags);
        tagGroupRepository.deleteById(id);
    }
    
    private List<FileTagDTO> buildTagTree(List<FileTag> allTags, Long parentId, Long groupId) {
        return allTags.stream()
                .filter(tag -> {
                    boolean parentMatch = (parentId == null && tag.getParentId() == null) || 
                           (parentId != null && parentId.equals(tag.getParentId()));
                    boolean groupMatch = (groupId == null) || groupId.equals(tag.getGroupId());
                    return parentMatch && groupMatch;
                })
                .map(tag -> {
                    FileTagDTO dto = new FileTagDTO();
                    dto.setId(tag.getId());
                    dto.setName(tag.getName());
                    dto.setColor(tag.getColor());
                    dto.setParentId(tag.getParentId());
                    dto.setGroupId(tag.getGroupId());
                    dto.setSortOrder(tag.getSortOrder());
                    dto.setChildren(buildTagTree(allTags, tag.getId(), groupId));
                    return dto;
                })
                .collect(Collectors.toList());
    }
    
    @Transactional
    public FileTagDTO createTag(FileTagDTO dto) {
        FileTag tag = new FileTag();
        tag.setName(dto.getName());
        tag.setColor(dto.getColor() != null ? dto.getColor() : "#409EFF");
        tag.setParentId(dto.getParentId());
        tag.setGroupId(dto.getGroupId());
        tag.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        
        FileTag saved = tagRepository.save(tag);
        return toDTO(saved);
    }
    
    @Transactional
    public FileTagDTO updateTag(Long id, FileTagDTO dto) {
        FileTag tag = tagRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("标签不存在"));
        
        if (dto.getName() != null) tag.setName(dto.getName());
        if (dto.getColor() != null) tag.setColor(dto.getColor());
        if (dto.getParentId() != null) tag.setParentId(dto.getParentId());
        if (dto.getGroupId() != null) tag.setGroupId(dto.getGroupId());
        if (dto.getSortOrder() != null) tag.setSortOrder(dto.getSortOrder());
        
        FileTag saved = tagRepository.save(tag);
        return toDTO(saved);
    }
    
    @Transactional
    public void deleteTag(Long id) {
        relationRepository.deleteByTagId(id);
        tagRepository.deleteById(id);
    }
    
    @Transactional
    public void addTagToFile(String filePath, Long serverId, Long tagId) {
        if (!relationRepository.findByFilePathAndServerIdAndTagId(filePath, serverId, tagId).isPresent()) {
            FileMetadata metadata = fileService.getOrCreateMetadata(serverId, filePath);
            
            FileTagRelation relation = new FileTagRelation();
            relation.setFilePath(filePath);
            relation.setServerId(serverId);
            relation.setTagId(tagId);
            relation.setFileId(metadata.getId());
            relationRepository.save(relation);
        }
    }
    
    @Transactional
    public void removeTagFromFile(String filePath, Long serverId, Long tagId) {
        relationRepository.findByFilePathAndServerIdAndTagId(filePath, serverId, tagId)
                .ifPresent(relationRepository::delete);
    }
    
    @Transactional
    public void setFileTags(String filePath, Long serverId, List<Long> tagIds) {
        relationRepository.deleteByFilePathAndServerId(filePath, serverId);
        
        FileMetadata metadata = fileService.getOrCreateMetadata(serverId, filePath);
        
        for (Long tagId : tagIds) {
            FileTagRelation relation = new FileTagRelation();
            relation.setFilePath(filePath);
            relation.setServerId(serverId);
            relation.setTagId(tagId);
            relation.setFileId(metadata.getId());
            relationRepository.save(relation);
        }
    }
    
    public List<FileTagDTO> getFileTags(String filePath, Long serverId) {
        List<FileTagRelation> relations = relationRepository.findByFilePathAndServerId(filePath, serverId);
        return relations.stream()
                .map(relation -> tagRepository.findById(relation.getTagId()))
                .filter(opt -> opt.isPresent())
                .map(opt -> toDTO(opt.get()))
                .collect(Collectors.toList());
    }
    
    public List<String> getFilesByTag(Long tagId) {
        return relationRepository.findByTagId(tagId).stream()
                .map(FileTagRelation::getFilePath)
                .collect(Collectors.toList());
    }
    
    private List<Long> getAllTagIdsIncludingChildren(Long tagId) {
        List<Long> tagIds = new ArrayList<>();
        tagIds.add(tagId);
        
        List<FileTag> allTags = tagRepository.findAll();
        collectChildTagIds(tagId, allTags, tagIds);
        
        return tagIds;
    }
    
    private void collectChildTagIds(Long parentId, List<FileTag> allTags, List<Long> tagIds) {
        for (FileTag tag : allTags) {
            if (parentId.equals(tag.getParentId())) {
                tagIds.add(tag.getId());
                collectChildTagIds(tag.getId(), allTags, tagIds);
            }
        }
    }
    
    public List<FileDTO> getFilesByTagWithDetails(Long tagId, Long serverId) {
        List<Long> tagIds = getAllTagIdsIncludingChildren(tagId);
        List<FileDTO> result = new ArrayList<>();
        
        for (Long tid : tagIds) {
            List<FileTagRelation> relations = relationRepository.findByTagId(tid);
            
            for (FileTagRelation relation : relations) {
                if (serverId != null && !serverId.equals(relation.getServerId())) {
                    continue;
                }
                
                try {
                    FileMetadata metadata = fileMetadataRepository
                            .findByServerIdAndPath(relation.getServerId(), relation.getFilePath())
                            .orElse(null);
                    
                    if (metadata != null) {
                        FileDTO dto = new FileDTO();
                        dto.setServerId(metadata.getServerId());
                        dto.setPath(metadata.getPath());
                        dto.setName(metadata.getName());
                        dto.setIsDirectory(metadata.getIsDirectory());
                        dto.setSize(metadata.getSize());
                        dto.setContentType(metadata.getContentType());
                        dto.setLastModified(metadata.getLastModified());
                        result.add(dto);
                    } else {
                        FileDTO dto = new FileDTO();
                        dto.setServerId(relation.getServerId());
                        dto.setPath(relation.getFilePath());
                        dto.setName(getFileName(relation.getFilePath()));
                        dto.setIsDirectory(false);
                        result.add(dto);
                    }
                } catch (Exception e) {
                    FileDTO dto = new FileDTO();
                    dto.setServerId(relation.getServerId());
                    dto.setPath(relation.getFilePath());
                    dto.setName(getFileName(relation.getFilePath()));
                    dto.setIsDirectory(false);
                    result.add(dto);
                }
            }
        }
        
        return result;
    }
    
    private FileDTO convertToFileDTO(DavResource resource, Long serverId, String path) {
        FileDTO dto = new FileDTO();
        dto.setServerId(serverId);
        dto.setPath(path);
        dto.setName(resource.getName());
        dto.setIsDirectory(resource.isDirectory());
        dto.setSize(resource.getContentLength());
        dto.setContentType(resource.getContentType());
        
        if (resource.getModified() != null) {
            dto.setLastModified(LocalDateTime.ofInstant(
                resource.getModified().toInstant(), ZoneId.systemDefault()));
        }
        
        String name = resource.getName().toLowerCase();
        if (resource.isDirectory()) {
            dto.setFileType("folder");
        } else if (name.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp|svg)$")) {
            dto.setFileType("image");
        } else if (name.matches(".*\\.(mp4|avi|mkv|mov|wmv|flv|webm)$")) {
            dto.setFileType("video");
        } else if (name.matches(".*\\.(mp3|wav|flac|aac|ogg|wma)$")) {
            dto.setFileType("audio");
        } else if (name.matches(".*\\.(pdf)$")) {
            dto.setFileType("pdf");
        } else if (name.matches(".*\\.(doc|docx)$")) {
            dto.setFileType("word");
        } else if (name.matches(".*\\.(xls|xlsx)$")) {
            dto.setFileType("excel");
        } else if (name.matches(".*\\.(ppt|pptx)$")) {
            dto.setFileType("ppt");
        } else if (name.matches(".*\\.(txt|md|json|xml|yaml|yml|html|css|js|ts|java|py|go|rs|c|cpp|h)$")) {
            dto.setFileType("text");
        } else if (name.matches(".*\\.(zip|rar|7z|tar|gz)$")) {
            dto.setFileType("archive");
        } else if (name.matches(".*\\.(epub|mobi|azw3)$")) {
            dto.setFileType("ebook");
        } else {
            dto.setFileType("file");
        }
        
        return dto;
    }
    
    private String getFileName(String path) {
        if (path == null || path.isEmpty()) return "";
        String[] parts = path.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : path;
    }
    
    private FileTagDTO toDTO(FileTag tag) {
        FileTagDTO dto = new FileTagDTO();
        dto.setId(tag.getId());
        dto.setName(tag.getName());
        dto.setColor(tag.getColor());
        dto.setParentId(tag.getParentId());
        dto.setGroupId(tag.getGroupId());
        dto.setSortOrder(tag.getSortOrder());
        dto.setChildren(new ArrayList<>());
        return dto;
    }
    
    private TagGroupDTO toGroupDTO(TagGroup group) {
        TagGroupDTO dto = new TagGroupDTO();
        dto.setId(group.getId());
        dto.setName(group.getName());
        dto.setSortOrder(group.getSortOrder());
        dto.setTags(new ArrayList<>());
        return dto;
    }
}