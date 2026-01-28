package com.prwatech.skillama.service.impl;

import com.prwatech.skillama.config.S3Config;
import com.prwatech.skillama.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {
    
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
        "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );
    
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    
    @Value("${file.upload.directory:uploads}")
    private String uploadDirectory;
    
    @Value("${file.upload.base-url:http://localhost:9090/files}")
    private String baseUrl;
    
    @Value("${file.upload.s3.base-url:https://presentation-image-courses.s3.ap-south-1.amazonaws.com}")
    private String s3BaseUrl;
    
    @Value("${aws.s3.bucket-name:presentation-image-courses}")
    private String bucketName;
    
    @Autowired
    private S3Client s3Client;
    
    @Override
    public String uploadImage(MultipartFile file, String subdirectory) throws IOException {
        validateImageFile(file);
        
        // Create directory structure
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String randomId = UUID.randomUUID().toString().substring(0, 8);
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        } else {
            // Determine extension from content type
            String contentType = file.getContentType();
            if (contentType != null) {
                if (contentType.contains("jpeg") || contentType.contains("jpg")) {
                    extension = ".jpg";
                } else if (contentType.contains("png")) {
                    extension = ".png";
                } else if (contentType.contains("gif")) {
                    extension = ".gif";
                } else if (contentType.contains("webp")) {
                    extension = ".webp";
                }
            }
        }
        
        String fileName = timestamp + "-" + randomId + extension;
        Path uploadPath = Paths.get(uploadDirectory, subdirectory);
        
        // Create directories if they don't exist
        Files.createDirectories(uploadPath);
        
        // Save file
        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        
        // Return URL
        return baseUrl + "/" + subdirectory + "/" + fileName;
    }
    
    @Override
    public String uploadImageForSubmodule(MultipartFile file, String courseId, Integer moduleOrder, Integer lessonOrder, Integer slideNumber) throws IOException {
        validateImageFile(file);
        
        // Format numbers as 2-digit strings (e.g., 3 -> "03", 28 -> "28")
        String moduleOrderStr = String.format("%02d", moduleOrder != null ? moduleOrder : 1);
        String lessonOrderStr = String.format("%02d", lessonOrder != null ? lessonOrder : 1);
        String slideNumberStr = String.format("%02d", slideNumber != null ? slideNumber : 1);
        
        // Determine file extension from content type or original filename
        String extension = ".png"; // Default to PNG
        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        } else if (contentType != null) {
            // Determine extension from content type
            if (contentType.contains("jpeg") || contentType.contains("jpg")) {
                extension = ".jpg";
            } else if (contentType.contains("png")) {
                extension = ".png";
            } else if (contentType.contains("gif")) {
                extension = ".gif";
            } else if (contentType.contains("webp")) {
                extension = ".webp";
            }
        }
        
        // Build S3-style path: courses/{courseId}/modules/{moduleOrder}/lessons/{lessonOrder}/slides/{slideNumber}.{ext}
        String s3Key = String.format("courses/%s/modules/%s/lessons/%s/slides/%s%s",
            courseId, moduleOrderStr, lessonOrderStr, slideNumberStr, extension);
        
        // Upload to S3 using IAM role credentials (EC2 instance profile)
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(contentType != null ? contentType : "image/png")
                .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            
            // Return S3 URL
            return s3BaseUrl + "/" + s3Key;
        } catch (S3Exception e) {
            throw new IOException("Failed to upload file to S3: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String uploadImageToS3(MultipartFile file, String s3Prefix) throws IOException {
        validateImageFile(file);
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String randomId = UUID.randomUUID().toString().substring(0, 8);
        String originalFilename = file.getOriginalFilename();
        String extension = ".png";
        
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        } else {
            String contentType = file.getContentType();
            if (contentType != null) {
                if (contentType.contains("jpeg") || contentType.contains("jpg")) extension = ".jpg";
                else if (contentType.contains("png")) extension = ".png";
                else if (contentType.contains("gif")) extension = ".gif";
                else if (contentType.contains("webp")) extension = ".webp";
            }
        }
        
        String s3Key = (s3Prefix != null && !s3Prefix.isEmpty() ? s3Prefix + "/" : "")
            + timestamp + "-" + randomId + extension;
        
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(file.getContentType() != null ? file.getContentType() : "image/png")
                .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            return s3BaseUrl + "/" + s3Key;
        } catch (S3Exception e) {
            throw new IOException("Failed to upload file to S3: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean deleteFile(String filePath) throws IOException {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }
        
        // Extract S3 key from URL
        String s3Key = null;
        if (filePath.startsWith(s3BaseUrl)) {
            s3Key = filePath.substring(s3BaseUrl.length());
            if (s3Key.startsWith("/")) {
                s3Key = s3Key.substring(1);
            }
        } else if (filePath.startsWith(baseUrl)) {
            // Local file
            String relativePath = filePath.substring(baseUrl.length());
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            Path path = Paths.get(uploadDirectory, relativePath);
            if (Files.exists(path)) {
                Files.delete(path);
                return true;
            }
            return false;
        } else {
            // Assume it's already a key
            s3Key = filePath;
        }
        
        // Delete from S3 using IAM role credentials (EC2 instance profile)
        if (s3Key != null) {
            try {
                DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
                
                s3Client.deleteObject(deleteObjectRequest);
                return true;
            } catch (S3Exception e) {
                throw new IOException("Failed to delete file from S3: " + e.getMessage(), e);
            }
        }
        
        return false;
    }
    
    @Override
    public void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        
        // Check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size of 5MB");
        }
        
        // Check content type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Invalid file type. Only JPG, PNG, GIF, and WebP are allowed.");
        }
        
        // Additional validation: check file extension
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
            List<String> allowedExtensions = Arrays.asList("jpg", "jpeg", "png", "gif", "webp");
            if (!allowedExtensions.contains(extension)) {
                throw new IllegalArgumentException("Invalid file extension. Only JPG, PNG, GIF, and WebP are allowed.");
            }
        }
    }
}

