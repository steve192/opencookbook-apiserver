package com.sterul.opencookbookapiserver.controllers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.sterul.opencookbookapiserver.Constants;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecipeImagesController {
    @GetMapping( value = "/recipe-images/{filename}", produces = MediaType.IMAGE_JPEG_VALUE)
    ResponseEntity<byte[]> getRecipeImage(@PathVariable String filename) throws IOException {
        var path = Paths.get(Constants.imageUploadDir).resolve(filename);
        
        return ResponseEntity
            .ok()
            .contentType(MediaType.IMAGE_JPEG)
            .body(Files.readAllBytes(path));
    }
}
