package com.sterul.opencookbookapiserver.controllers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.sterul.opencookbookapiserver.Constants;
import com.sterul.opencookbookapiserver.util.FileUploadUtil;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/recipes-images")
public class RecipeImagesController {
    @GetMapping( value = "/{filename}", produces = MediaType.IMAGE_JPEG_VALUE)
    ResponseEntity<byte[]> getRecipeImage(@PathVariable String filename) throws IOException {
        var path = Paths.get(Constants.imageUploadDir).resolve(filename);
        
        return ResponseEntity
            .ok()
            .contentType(MediaType.IMAGE_JPEG)
            .body(Files.readAllBytes(path));
    }

    static class ImageCreationResult {
        private String uuid;

        public String getUuid() {
            return uuid;
        }

        public void setUuid(String uuid) {
            this.uuid = uuid;
        }

    }

    @PostMapping("/")
    public void uploadRecipeImage(@PathVariable Long id, @RequestParam("image") MultipartFile multipartFile) throws IOException {

        String filename = StringUtils.cleanPath(multipartFile.getOriginalFilename());
        FileUploadUtil.saveFile(Constants.imageUploadDir, filename, multipartFile);

    }
}
