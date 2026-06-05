package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.StudyMaterialDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.CourseStudyMaterial;
import com.prwatech.skillama.repository.CourseStudyMaterialRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseStudyMaterialService {

    private final CourseStudyMaterialRepository repository;
    private final FileStorageService fileStorageService;
    private final CourseService courseService;

    @Value("${file.upload.s3.study-materials-prefix:courses}")
    private String studyMaterialsPrefix;

    public List<StudyMaterialDTO> listForCourse(String courseId) {
        ensureCourseExists(courseId);
        return repository.findByCourseIdOrderBySortOrderAscUploadedAtAsc(courseId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public StudyMaterialDTO upload(
            String courseId,
            MultipartFile file,
            String title,
            String description,
            Integer sortOrder,
            String adminUserId) throws IOException {
        ensureCourseExists(courseId);
        // Folder is keyed by Mongo course id (unique), never by display name — duplicate course titles are isolated.
        // Object keys also include timestamp + uuid so re-uploading the same filename does not overwrite.
        String prefix = studyMaterialsPrefix + "/" + courseId + "/materials";
        String fileUrl = fileStorageService.uploadDocumentToS3(file, prefix);

        CourseStudyMaterial material = CourseStudyMaterial.builder()
                .courseId(courseId)
                .title(StringUtils.hasText(title) ? title.trim() : extractFileName(file.getOriginalFilename()))
                .description(description != null ? description.trim() : null)
                .fileName(file.getOriginalFilename())
                .fileUrl(fileUrl)
                .s3Key(extractS3Key(fileUrl))
                .contentType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .sortOrder(sortOrder != null ? sortOrder : nextSortOrder(courseId))
                .uploadedBy(adminUserId)
                .uploadedAt(IndiaTime.now())
                .build();

        return toDto(repository.save(material));
    }

    public void delete(String courseId, String materialId) throws IOException {
        CourseStudyMaterial material = repository.findById(materialId)
                .filter(m -> courseId.equals(m.getCourseId()))
                .orElseThrow(() -> new ResourceNotFoundException("Study material not found"));

        if (StringUtils.hasText(material.getFileUrl()) && fileStorageService.isManagedStorageUrl(material.getFileUrl())) {
            try {
                fileStorageService.deleteFile(material.getFileUrl());
            } catch (IOException ignored) {
                // best-effort
            }
        }
        repository.delete(material);
    }

    private void ensureCourseExists(String courseId) {
        courseService.findActiveById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
    }

    private int nextSortOrder(String courseId) {
        return repository.findByCourseIdOrderBySortOrderAscUploadedAtAsc(courseId).size() + 1;
    }

    private StudyMaterialDTO toDto(CourseStudyMaterial m) {
        return StudyMaterialDTO.builder()
                .id(m.getId())
                .courseId(m.getCourseId())
                .title(m.getTitle())
                .description(m.getDescription())
                .fileName(m.getFileName())
                .fileUrl(m.getFileUrl())
                .contentType(m.getContentType())
                .fileSizeBytes(m.getFileSizeBytes())
                .sortOrder(m.getSortOrder())
                .uploadedAt(m.getUploadedAt())
                .build();
    }

    private String extractFileName(String name) {
        if (!StringUtils.hasText(name)) {
            return "Study material";
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private String extractS3Key(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return null;
        }
        int idx = fileUrl.indexOf(".amazonaws.com/");
        if (idx >= 0) {
            return fileUrl.substring(idx + ".amazonaws.com/".length());
        }
        return null;
    }
}
