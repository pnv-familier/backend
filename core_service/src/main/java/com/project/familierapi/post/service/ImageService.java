package com.project.familierapi.post.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.project.familierapi.post.exception.InvalidImageException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ImageService {
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final long MAX_VIDEO_SIZE = 200 * 1024 * 1024;
    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList("image/jpeg", "image/png");
    private static final List<String> ALLOWED_VIDEO_TYPES = Arrays.asList("video/mp4", "video/quicktime", "video/x-msvideo");
    private static final List<String> ALLOWED_IMAGE_EXTENSIONS = Arrays.asList(".jpg", ".jpeg", ".png");
    private static final List<String> ALLOWED_VIDEO_EXTENSIONS = Arrays.asList(".mp4", ".mov", ".avi");

    private final Cloudinary cloudinary;

    public void validateImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidImageException("Image file is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidImageException("Invalid image. Please upload a JPG or PNG file smaller than 5MB.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidImageException("Invalid image. Please upload a JPG or PNG file smaller than 5MB.");
        }

        String filename = file.getOriginalFilename();
        if (filename != null) {
            String extension = filename.substring(filename.lastIndexOf(".")).toLowerCase();
            if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
                throw new InvalidImageException("Invalid image. Please upload a JPG or PNG file smaller than 5MB.");
            }
        }
    }

    public void validateVideo(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidImageException("Video file is empty");
        }

        if (file.getSize() > MAX_VIDEO_SIZE) {
            throw new InvalidImageException("Invalid video. Please upload a MP4, MOV, or AVI file smaller than 200MB.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_VIDEO_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidImageException("Invalid video. Please upload a MP4, MOV, or AVI file smaller than 200MB.");
        }

        String filename = file.getOriginalFilename();
        if (filename != null) {
            String extension = filename.substring(filename.lastIndexOf(".")).toLowerCase();
            if (!ALLOWED_VIDEO_EXTENSIONS.contains(extension)) {
                throw new InvalidImageException("Invalid video. Please upload a MP4, MOV, or AVI file smaller than 200MB.");
            }
        }
    }

    public String uploadImage(MultipartFile file) {
        validateImage(file);
        
        try {
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), 
                ObjectUtils.asMap(
                    "folder", "familier/posts",
                    "resource_type", "image"
                ));
            return (String) uploadResult.get("secure_url");
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload image to Cloudinary", e);
        }
    }

    public String uploadVideo(MultipartFile file) {
        validateVideo(file);
        
        try {
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), 
                ObjectUtils.asMap(
                    "folder", "familier/posts",
                    "resource_type", "video"
                ));
            return (String) uploadResult.get("secure_url");
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload video to Cloudinary", e);
        }
    }
}
