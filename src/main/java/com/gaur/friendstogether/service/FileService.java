package com.gaur.friendstogether.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j // <--- Bas ye add karo, error khatam ho jayegi!
public class FileService {


    private final S3Client s3Client;

    @Value("${app.s3.bucket}")
    private String bucketName;

    @Value("${cloud.aws.region.static}")
    private String region;

    // FileService.java mein ye method add karein
    public void deleteFile(String fileUrl) {
        try {
            // 1. URL se "Key" nikalna (e.g., profiles/abc-123.jpg)
            // URL format: https://bucket.s3.region.amazonaws.com/profiles/name.jpg
            String key = fileUrl.substring(fileUrl.lastIndexOf("profiles/"));

            // 2. S3 se delete request
            software.amazon.awssdk.services.s3.model.DeleteObjectRequest deleteObjectRequest =
                    software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("Old file deleted from S3: {}", key);
        } catch (Exception e) {
            log.error("Failed to delete old file from S3: {}", e.getMessage());
            // Note: Delete fail hone par hum exception throw nahi karenge
            // taaki user ka upload na ruke. Bas log kar denge.
        }
    }

    public String uploadProfileImage(MultipartFile file) throws IOException {
        // 1. Unique file name generate karna
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        String key = "profiles/" + fileName;

        // 2. PutObjectRequest banana
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .build();

        // 3. S3 par upload karna (V2 Style)
        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        // 4. Manually Public URL construct karna (V2 mein getUrl method bean mein nahi hota)
        // Format: https://bucket-name.s3.region.amazonaws.com/key
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, key);
    }
}

// ===============================
// AWS V1 DEPRICATED DEPENDENCY CODE
// ===============================

//package com.gaur.friendstogether.service;
//
//import com.amazonaws.services.s3.AmazonS3;
//import com.amazonaws.services.s3.model.ObjectMetadata;
//import com.amazonaws.services.s3.model.PutObjectRequest;
//import lombok.RequiredArgsConstructor;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.IOException;
//import java.util.UUID;
//
//@Service
//@RequiredArgsConstructor
//public class FileService {
//
//    private final AmazonS3 s3Client;
//
//    @Value("${app.s3.bucket}")
//    private String bucketName;
//
//    public String uploadProfileImage(MultipartFile file) throws IOException {
//        // Unique file name
//        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
//        String key = "profiles/" + fileName;
//
//        // Metadata set karna (zaroori hai V1 mein)
//        ObjectMetadata metadata = new ObjectMetadata();
//        metadata.setContentType(file.getContentType());
//        metadata.setContentLength(file.getSize());
//
//        // Upload to S3
//        s3Client.putObject(new PutObjectRequest(bucketName, key, file.getInputStream(), metadata));
//
//        // Get Public URL
//        return s3Client.getUrl(bucketName, key).toString();
//    }
//}

