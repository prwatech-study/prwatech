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
}

