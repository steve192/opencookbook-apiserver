package com.sterul.opencookbookapiserver.controllers;

import java.io.IOException;
import java.util.NoSuchElementException;

import javax.servlet.http.HttpServletResponse;

import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.services.IllegalFiletypeException;
import com.sterul.opencookbookapiserver.services.RecipeImageService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    @Autowired
    RecipeImageService recipeImageService;

    @GetMapping(value = "/{uuid}", produces = MediaType.IMAGE_JPEG_VALUE)
    ResponseEntity<byte[]> getRecipeImage(@PathVariable String uuid, HttpServletResponse response) throws NoSuchElementException {
        byte[] imageData;
        try {
            imageData = recipeImageService.getImage(uuid);
        } catch (IOException e) {
            throw new NoSuchElementException();
        }

        response.setHeader("Cache-Control", "no-transform, public, max-age=86400");
        return ResponseEntity.ok()
                // .contentType(MediaType.IMAGE_JPEG)
                .body(imageData);
    }

    

    @PostMapping("")
    public RecipeImage uploadRecipeImage(@RequestParam("image") MultipartFile multipartFile)
            throws IOException, IllegalFiletypeException {
        return recipeImageService.saveNewImage(multipartFile.getInputStream(), multipartFile.getSize());
    }
}
