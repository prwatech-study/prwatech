package com.prwatech.skillama.service.impl;

import com.prwatech.skillama.config.S3Config;
import com.prwatech.skillama.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
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
import com.prwatech.skillama.util.IndiaTime;
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
    private static final long MAX_VIDEO_FILE_SIZE = 500L * 1024 * 1024; // 500MB

    private static final List<String> ALLOWED_VIDEO_CONTENT_TYPES = Arrays.asList(
        "video/mp4", "video/webm", "video/quicktime", "video/x-msvideo"
    );

    private static final List<String> ALLOWED_DOCUMENT_CONTENT_TYPES = Arrays.asList(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/zip",
        "application/x-zip-compressed",
        "text/plain",
        "text/csv",
        "image/jpeg",
        "image/jpg",
        "image/png"
    );

    private static final long MAX_DOCUMENT_FILE_SIZE = 50L * 1024 * 1024; // 50MB
    
    @Value("${file.upload.directory:uploads}")
    private String uploadDirectory;
    
    @Value("${file.upload.base-url:http://localhost:9090/files}")
    private String baseUrl;
    
    @Value("${file.upload.s3.base-url:https://presentation-image-courses.s3.ap-south-1.amazonaws.com}")
    private String s3BaseUrl;

    @Value("${file.upload.s3.study-materials-base-url:https://skillama-course-materials.s3.ap-south-1.amazonaws.com}")
    private String studyMaterialsBaseUrl;
    
    @Value("${aws.s3.bucket-name:presentation-image-courses}")
    private String bucketName;

    @Value("${aws.s3.study-materials-bucket-name:skillama-course-materials}")
    private String studyMaterialsBucketName;
    
    @Autowired
    private S3Client s3Client;
    
    @Override
    public String uploadImage(MultipartFile file, String subdirectory) throws IOException {
        validateImageFile(file);
        
        // Create directory structure
        String timestamp = IndiaTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
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
    public String uploadImageForSubmoduleById(MultipartFile file, String courseId, String moduleId, int submoduleIdx, Integer slideNumber) throws IOException {
        validateImageFile(file);

        // Unique key per Mongo module + submodule index (order fields can duplicate across modules).
        String submoduleIdxStr = String.format("%02d", submoduleIdx + 1);
        String slideNumberStr = String.format("%02d", slideNumber != null ? slideNumber : 1);

        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();
        String extension = resolveImageExtension(originalFilename, contentType);

        String s3Key = String.format("courses/%s/modules/%s/submodules/%s/slides/%s%s",
            courseId, moduleId, submoduleIdxStr, slideNumberStr, extension);

        return uploadToS3WithKey(file, s3Key, contentType);
    }

    private String resolveImageExtension(String originalFilename, String contentType) {
        String extension = ".png";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        } else if (contentType != null) {
            if (contentType.contains("jpeg") || contentType.contains("jpg")) extension = ".jpg";
            else if (contentType.contains("png")) extension = ".png";
            else if (contentType.contains("gif")) extension = ".gif";
            else if (contentType.contains("webp")) extension = ".webp";
        }
        return extension;
    }

    private String uploadToS3WithKey(MultipartFile file, String s3Key, String contentType) throws IOException {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(contentType != null ? contentType : "image/png")
                .cacheControl("max-age=0, no-cache, no-store, must-revalidate")
                .build();

            long size = file.getSize();
            RequestBody body;
            if (size >= 0) {
                body = RequestBody.fromInputStream(file.getInputStream(), size);
            } else {
                byte[] bytes = file.getBytes();
                body = RequestBody.fromBytes(bytes);
            }
            s3Client.putObject(putObjectRequest, body);

            long version = System.currentTimeMillis();
            return s3BaseUrl + "/" + s3Key + "?v=" + version;
        } catch (S3Exception e) {
            throw new IOException("Failed to upload file to S3: " + e.getMessage(), e);
        }
    }

    @Override
    public String uploadGeneratedImageForSubmodule(byte[] data, String courseId, String moduleId, int submoduleIdx, String contentType) throws IOException {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Generated image data is empty");
        }
        String submoduleIdxStr = String.format("%02d", submoduleIdx + 1);
        String slideNumberStr = "01";
        String extension = resolveGeneratedExtension(contentType);
        String resolvedContentType = (contentType != null && !contentType.isBlank()) ? contentType : "image/svg+xml";

        String s3Key = String.format("courses/%s/modules/%s/submodules/%s/slides/%s%s",
            courseId, moduleId, submoduleIdxStr, slideNumberStr, extension);

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(resolvedContentType)
                .cacheControl("max-age=0, no-cache, no-store, must-revalidate")
                .build();
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(data));
            long version = System.currentTimeMillis();
            return s3BaseUrl + "/" + s3Key + "?v=" + version;
        } catch (S3Exception e) {
            throw new IOException("Failed to upload generated image to S3: " + e.getMessage(), e);
        }
    }

    private String resolveGeneratedExtension(String contentType) {
        if (contentType == null) {
            return ".svg";
        }
        String ct = contentType.toLowerCase();
        if (ct.contains("svg")) return ".svg";
        if (ct.contains("png")) return ".png";
        if (ct.contains("jpeg") || ct.contains("jpg")) return ".jpg";
        if (ct.contains("webp")) return ".webp";
        return ".svg";
    }

    @Override
    public String uploadImageForSubmodule(MultipartFile file, String courseId, Integer moduleOrder, Integer lessonOrder, Integer slideNumber) throws IOException {
        validateImageFile(file);
        
        // S3 path structure (must match): bucket/courses/{courseId}/modules/{MM}/lessons/{LL}/slides/{SS}.{ext}
        // Example: presentation-image-courses/courses/6817bbe4cb6b8135daecc428/modules/03/lessons/01/slides/01.png
        String moduleOrderStr = String.format("%02d", moduleOrder != null ? moduleOrder : 1);
        String lessonOrderStr = String.format("%02d", lessonOrder != null ? lessonOrder : 1);
        String slideNumberStr = String.format("%02d", slideNumber != null ? slideNumber : 1);
        
        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();
        String extension = resolveImageExtension(originalFilename, contentType);
        
        // Key: courses/{courseId}/modules/03/lessons/01/slides/01.png
        String s3Key = String.format("courses/%s/modules/%s/lessons/%s/slides/%s%s",
            courseId, moduleOrderStr, lessonOrderStr, slideNumberStr, extension);
        
        return uploadToS3WithKey(file, s3Key, contentType);
    }
    
    @Override
    public String uploadImageToS3(MultipartFile file, String s3Prefix) throws IOException {
        validateImageFile(file);
        
        String timestamp = IndiaTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
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
    public boolean isManagedStorageUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        String u = url.trim();
        if (u.startsWith(s3BaseUrl) || u.startsWith(studyMaterialsBaseUrl)) {
            return true;
        }
        return u.startsWith(baseUrl);
    }

    @Override
    public boolean deleteFile(String filePath) throws IOException {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }

        if (filePath.startsWith(baseUrl)) {
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
        }

        S3ObjectRef ref = resolveS3ObjectRef(filePath);
        if (ref == null) {
            return false;
        }

        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(ref.bucket())
                .key(ref.key())
                .build();
            s3Client.deleteObject(deleteObjectRequest);
            return true;
        } catch (S3Exception e) {
            throw new IOException("Failed to delete file from S3: " + e.getMessage(), e);
        }
    }

    private record S3ObjectRef(String bucket, String key) {}

    private S3ObjectRef resolveS3ObjectRef(String filePath) {
        if (filePath.startsWith(studyMaterialsBaseUrl)) {
            return new S3ObjectRef(
                studyMaterialsBucketName,
                extractS3KeyFromUrl(filePath, studyMaterialsBaseUrl));
        }
        if (filePath.startsWith(s3BaseUrl)) {
            return new S3ObjectRef(bucketName, extractS3KeyFromUrl(filePath, s3BaseUrl));
        }
        // Legacy: bare key — assume primary (presentation) bucket
        if (!filePath.startsWith("http")) {
            return new S3ObjectRef(bucketName, filePath);
        }
        return null;
    }

    private static String extractS3KeyFromUrl(String filePath, String baseUrl) {
        String key = filePath.substring(baseUrl.length());
        if (key.startsWith("/")) {
            key = key.substring(1);
        }
        int queryIndex = key.indexOf('?');
        if (queryIndex >= 0) {
            key = key.substring(0, queryIndex);
        }
        return key;
    }
    
    @Override
    public String uploadVideoToS3(MultipartFile file, String s3Prefix) throws IOException {
        validateVideoFile(file);

        String timestamp = IndiaTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String randomId = UUID.randomUUID().toString().substring(0, 8);
        String originalFilename = file.getOriginalFilename();
        String extension = ".mp4";

        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        } else {
            String contentType = file.getContentType();
            if (contentType != null) {
                if (contentType.contains("webm")) extension = ".webm";
                else if (contentType.contains("quicktime")) extension = ".mov";
                else if (contentType.contains("msvideo") || contentType.contains("avi")) extension = ".avi";
            }
        }

        String prefix = (s3Prefix != null && !s3Prefix.isEmpty()) ? s3Prefix.replaceAll("/$", "") + "/" : "";
        String s3Key = prefix + timestamp + "-" + randomId + extension;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(file.getContentType() != null ? file.getContentType() : "video/mp4")
                .build();

            long size = file.getSize();
            RequestBody body;
            if (size >= 0) {
                body = RequestBody.fromInputStream(file.getInputStream(), size);
            } else {
                byte[] bytes = file.getBytes();
                body = RequestBody.fromBytes(bytes);
            }
            s3Client.putObject(putObjectRequest, body);
            return s3BaseUrl + "/" + s3Key;
        } catch (S3Exception e) {
            throw new IOException("Failed to upload video to S3: " + e.getMessage(), e);
        }
    }

    @Override
    public void validateVideoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Video file is required");
        }
        if (file.getSize() > MAX_VIDEO_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size of 500MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_VIDEO_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported video type. Use MP4, WebM, MOV, or AVI.");
        }
    }

    @Override
    public String uploadDocumentToS3(MultipartFile file, String s3Prefix) throws IOException {
        validateDocumentFile(file);

        String timestamp = IndiaTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String randomId = UUID.randomUUID().toString().substring(0, 8);
        String originalFilename = file.getOriginalFilename();
        String extension = "";

        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        } else {
            String contentType = file.getContentType();
            if (contentType != null) {
                if (contentType.contains("pdf")) extension = ".pdf";
                else if (contentType.contains("wordprocessingml")) extension = ".docx";
                else if (contentType.contains("msword")) extension = ".doc";
                else if (contentType.contains("spreadsheetml")) extension = ".xlsx";
                else if (contentType.contains("presentationml")) extension = ".pptx";
                else if (contentType.contains("zip")) extension = ".zip";
                else if (contentType.contains("plain")) extension = ".txt";
                else if (contentType.contains("csv")) extension = ".csv";
                else if (contentType.contains("jpeg") || contentType.contains("jpg")) extension = ".jpg";
                else if (contentType.contains("png")) extension = ".png";
            }
        }

        String safeName = sanitizeFileName(originalFilename);
        String prefix = (s3Prefix != null && !s3Prefix.isEmpty()) ? s3Prefix.replaceAll("/$", "") + "/" : "";
        String s3Key = prefix + timestamp + "-" + randomId + (safeName != null ? "-" + safeName : "") + extension;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(studyMaterialsBucketName)
                .key(s3Key)
                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .build();

            long size = file.getSize();
            RequestBody body;
            if (size >= 0) {
                body = RequestBody.fromInputStream(file.getInputStream(), size);
            } else {
                byte[] bytes = file.getBytes();
                body = RequestBody.fromBytes(bytes);
            }
            s3Client.putObject(putObjectRequest, body);
            return studyMaterialsBaseUrl + "/" + s3Key;
        } catch (S3Exception e) {
            throw new IOException("Failed to upload document to study-materials S3: " + e.getMessage(), e);
        }
    }

    @Override
    public void validateDocumentFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        if (file.getSize() > MAX_DOCUMENT_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size of 50MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_DOCUMENT_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename != null) {
                String ext = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
                List<String> allowedExtensions = Arrays.asList(
                    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip", "txt", "csv", "jpg", "jpeg", "png"
                );
                if (allowedExtensions.contains(ext)) {
                    return;
                }
            }
            throw new IllegalArgumentException(
                "Unsupported file type. Allowed: PDF, DOC/DOCX, XLS/XLSX, PPT/PPTX, ZIP, TXT, CSV, JPG, PNG.");
        }
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String base = slash >= 0 ? name.substring(slash + 1) : name;
        if (base.contains(".")) {
            base = base.substring(0, base.lastIndexOf('.'));
        }
        return base.replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase();
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

