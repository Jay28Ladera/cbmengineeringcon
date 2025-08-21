// src/main/java/com/asmsi/admin_system/service/FileStorageService.java
package com.asmsi.admin_system.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageService {
  private final Path root = Paths.get("uploads/faculty");

  public FileStorageService() throws IOException {
    Files.createDirectories(root);
  }

  public String store(MultipartFile file) throws IOException {
    if (file == null || file.isEmpty()) return null;
    String ext = getExt(file.getOriginalFilename());
    String filename = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
    Path dest = root.resolve(filename).normalize();
    Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
    return "/uploads/faculty/" + filename;
  }

  private String getExt(String name){
    if (name == null) return "";
    int i = name.lastIndexOf('.');
    return (i >= 0) ? name.substring(i+1) : "";
  }
}
