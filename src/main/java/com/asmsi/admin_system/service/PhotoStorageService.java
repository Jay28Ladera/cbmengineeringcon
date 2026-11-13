package com.asmsi.admin_system.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Stores staff photos. Uses Cloudinary when its keys are set, because hosts such as Render
 * wipe the local disk on every deploy. Falls back to the local uploads/ folder otherwise
 * (fine for development).
 */
@Service
public class PhotoStorageService {

    private static final Logger log = LoggerFactory.getLogger(PhotoStorageService.class);
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    public static final long MAX_BYTES = 3 * 1024 * 1024;

    @Value("${cloudinary.cloud-name:}") private String cloudName;
    @Value("${cloudinary.api-key:}") private String apiKey;
    @Value("${cloudinary.api-secret:}") private String apiSecret;

    /** Thrown with a message that can be shown to the user. */
    public static class PhotoException extends Exception {
        public PhotoException(String message) { super(message); }
    }

    /** Saves the photo and returns its public URL. */
    public String store(MultipartFile file, String folder) throws PhotoException {
        if (file == null || file.isEmpty()) throw new PhotoException("No photo was selected.");
        if (file.getSize() > MAX_BYTES) throw new PhotoException("The photo is larger than 3 MB.");
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!TYPES.contains(type)) throw new PhotoException("Use a JPG, PNG or WebP photo.");

        if (!blank(cloudName) && !blank(apiKey) && !blank(apiSecret)) {
            try {
                Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
                        "cloud_name", cloudName, "api_key", apiKey, "api_secret", apiSecret, "secure", true));
                Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                        "folder", "cbm/" + folder,
                        "resource_type", "image",
                        "transformation", "c_fill,g_face,w_400,h_400,q_auto,f_auto"));
                Object url = result.get("secure_url");
                if (url != null) return url.toString();
            } catch (Exception e) {
                log.warn("Cloudinary upload failed, saving locally instead: {}", e.getMessage());
            }
        }

        try {
            String ext = switch (type) { case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> ".jpg"; };
            Path dir = Paths.get("uploads", folder);
            Files.createDirectories(dir);
            String name = UUID.randomUUID() + ext;
            Files.copy(file.getInputStream(), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/" + folder + "/" + name;
        } catch (IOException e) {
            log.error("Could not save photo: {}", e.getMessage());
            throw new PhotoException("The photo could not be saved. Please try again.");
        }
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
