package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.ImageUploadResponseDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CurriculumMediaService {

    private final S3StorageService s3StorageService;
    private final CourseCurriculumRepository curriculumRepository;

    public ImageUploadResponseDTO uploadImage(MultipartFile image) throws IOException {
        S3StorageService.UploadResult result = s3StorageService.uploadCurriculumImage(image);
        return toDto(result);
    }

    public ImageUploadResponseDTO uploadSubmoduleImage(
            String moduleId, int submoduleIndex, MultipartFile image) throws IOException {
        CourseCurriculum module = curriculumRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found: " + moduleId));

        List<CourseCurriculum.Submodule> submodules = module.getSubmodules();
        if (submodules == null || submoduleIndex < 0 || submoduleIndex >= submodules.size()) {
            throw new ResourceNotFoundException("Submodule index out of range: " + submoduleIndex);
        }

        S3StorageService.UploadResult result = s3StorageService.uploadCurriculumImage(image);
        submodules.get(submoduleIndex).setImagePath(result.getUrl());
        module.setSubmodules(submodules);
        module.setUpdatedAt(LocalDateTime.now());
        curriculumRepository.save(module);

        ImageUploadResponseDTO dto = toDto(result);
        dto.setModuleId(moduleId);
        dto.setSubmoduleIndex(submoduleIndex);
        dto.setSubmoduleId(moduleId + ":" + submoduleIndex);
        return dto;
    }

    public ImageUploadResponseDTO deleteSubmoduleImage(String moduleId, int submoduleIndex) {
        CourseCurriculum module = curriculumRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found: " + moduleId));

        List<CourseCurriculum.Submodule> submodules = module.getSubmodules();
        if (submodules == null || submoduleIndex < 0 || submoduleIndex >= submodules.size()) {
            throw new ResourceNotFoundException("Submodule index out of range: " + submoduleIndex);
        }

        String existingPath = submodules.get(submoduleIndex).getImagePath();
        String s3Key = s3StorageService.extractKeyFromUrl(existingPath);
        if (s3Key != null) {
            s3StorageService.deleteObject(s3Key);
        }

        submodules.get(submoduleIndex).setImagePath(null);
        module.setSubmodules(submodules);
        module.setUpdatedAt(LocalDateTime.now());
        curriculumRepository.save(module);

        ImageUploadResponseDTO dto = new ImageUploadResponseDTO();
        dto.setSubmoduleId(moduleId + ":" + submoduleIndex);
        dto.setImagePath(null);
        dto.setImageUrl(null);
        return dto;
    }

    private ImageUploadResponseDTO toDto(S3StorageService.UploadResult result) {
        ImageUploadResponseDTO dto = new ImageUploadResponseDTO();
        dto.setImagePath(result.getUrl());
        dto.setImageUrl(result.getUrl());
        dto.setFileName(result.getOriginalFileName());
        dto.setFileSize(result.getFileSizeBytes());
        dto.setContentType(result.getContentType());
        return dto;
    }
}
