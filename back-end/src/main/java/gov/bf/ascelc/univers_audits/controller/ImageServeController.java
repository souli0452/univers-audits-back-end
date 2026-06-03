package gov.bf.ascelc.univers_audits.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;


@Slf4j
@RestController
@RequestMapping("/api/v1/public/images")
public class ImageServeController {

    @Value("${storage.upload-dir:C:/asce-lc/uploads}")
    private String uploadDir;

    @GetMapping("/portal/{context}/{filename}")
    public ResponseEntity<Resource> serveImage(
            @PathVariable String context,
            @PathVariable String filename) {

        if (filename.contains("..") || context.contains("..")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Path filePath = Paths.get(uploadDir, "portal", context)
                    .resolve(filename).normalize();

            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            MediaType mediaType = getMediaType(filename);

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private MediaType getMediaType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png"))  return MediaType.IMAGE_PNG;
        if (lower.endsWith(".gif"))  return MediaType.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (lower.endsWith(".svg"))  return MediaType.parseMediaType("image/svg+xml");
        return MediaType.IMAGE_JPEG; // défaut
    }
}