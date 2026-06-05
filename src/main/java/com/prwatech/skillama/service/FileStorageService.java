package com.prwatech.skillama.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Service for handling file storage operations.
 * Supports local file system storage (can be extended for S3/cloud storage).
 */
public interface FileStorageService {
    
    /**
     * Uploads an image file and returns the file URL/path.
     * 
     * @param file The image file to upload
     * @param subdirectory Subdirectory to store the file (e.g., "curriculum/images")
     * @return The URL/path of the uploaded file
     * @throws IOException if file operations fail
     * @throws IllegalArgumentException if file validation fails
     */
    String uploadImage(MultipartFile file, String subdirectory) throws IOException;
    
    /**
     * Uploads an image file following S3 path pattern: courses/{courseId}/modules/{moduleOrder}/lessons/{lessonOrder}/slides/{slideNumber}.png
     * 
     * @param file The image file to upload
     * @param courseId The course ID
     * @param moduleOrder The module order (1-based)
     * @param lessonOrder The lesson/submodule order (1-based)
     * @param slideNumber The slide number (default: 01)
     * @return The S3-style URL/path of the uploaded file
     * @throws IOException if file operations fail
     * @throws IllegalArgumentException if file validation fails
     */
    String uploadImageForSubmodule(MultipartFile file, String courseId, Integer moduleOrder, Integer lessonOrder, Integer slideNumber) throws IOException;
    
    /**
     * Uploads an image file to S3 under the given prefix (e.g. "curriculum/images").
     * Used for generic curriculum image upload when module/submodule context is not yet known.
     *
     * @param file The image file to upload
     * @param s3Prefix S3 key prefix (e.g. "curriculum/images")
     * @return The full S3 URL of the uploaded file
     * @throws IOException if upload fails
     * @throws IllegalArgumentException if file validation fails
     */
    String uploadImageToS3(MultipartFile file, String s3Prefix) throws IOException;
    
    /**
     * @return true if this URL points at an object in our configured S3 bucket (safe to pass to {@link #deleteFile}).
     */
    boolean isManagedStorageUrl(String url);

    /**
     * Deletes a file by its URL/path.
     * 
     * @param filePath The URL/path of the file to delete
     * @return true if file was deleted, false if file doesn't exist
     * @throws IOException if file deletion fails
     */
    boolean deleteFile(String filePath) throws IOException;
    
    /**
     * Validates an image file.
     * 
     * @param file The file to validate
     * @throws IllegalArgumentException if validation fails
     */
    void validateImageFile(MultipartFile file);

    /**
     * Uploads a video file to S3 under the given prefix (e.g. "demo-video").
     */
    String uploadVideoToS3(MultipartFile file, String s3Prefix) throws IOException;

    void validateVideoFile(MultipartFile file);

    /**
     * Uploads a document (PDF, DOCX, ZIP, etc.) to the dedicated study-materials S3 bucket
     * under the given prefix (e.g. courses/{courseId}/materials).
     */
    String uploadDocumentToS3(MultipartFile file, String s3Prefix) throws IOException;

    void validateDocumentFile(MultipartFile file);
}

