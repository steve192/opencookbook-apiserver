package com.sterul.opencookbookapiserver.controllers;

import com.sterul.opencookbookapiserver.controllers.exceptions.NotAuthorizedException;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.services.IllegalFiletypeException;
import com.sterul.opencookbookapiserver.services.RecipeImageService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/recipes-images")
@Tag(name = "Recipe images", description = "Managing recipe images")
public class RecipeImagesController extends BaseController {

    @Autowired
    RecipeImageService recipeImageService;

    @Operation(summary = "Fetch single recipe image")
    @GetMapping(value = "/{uuid}", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> getRecipeImage(@PathVariable String uuid, HttpServletResponse response)
            throws ElementNotFound, NotAuthorizedException {

        if (!recipeImageService.hasAccessPermissionToRecipeImage(uuid, getLoggedInUser())) {
            throw new NotAuthorizedException();
        }

        byte[] imageData;
        try {
            imageData = recipeImageService.getImage(uuid);
        } catch (IOException e) {
            throw new ElementNotFound();
        }

        response.setHeader("Cache-Control", "no-transform, private, max-age=86400");
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(imageData);
    }

    @Operation(summary = "Fetch single recipe image thumbnail")
    @GetMapping(value = "/thumbnail/{uuid}", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> getRecipeThumbnailImage(@PathVariable String uuid, HttpServletResponse response)
            throws ElementNotFound, NotAuthorizedException {

        if (!recipeImageService.hasAccessPermissionToRecipeImage(uuid, getLoggedInUser())) {
            throw new NotAuthorizedException();
        }

        byte[] imageData;
        try {
            imageData = recipeImageService.getThumbnailImage(uuid);
        } catch (IOException e) {
            throw new ElementNotFound();
        }

        response.setHeader("Cache-Control", "no-transform, private, max-age=86400");
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(imageData);
    }

    @Operation(summary = "Upload a new image", description = "Upload an image as multipart file. The images uuid can later on be assigned to a recipe. If they are not assigned they will be deleted after a while")
    @PostMapping(value = "", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RecipeImage uploadRecipeImage(@RequestParam("image") MultipartFile multipartFile)
            throws IOException, IllegalFiletypeException {
        return recipeImageService.saveNewImage(multipartFile.getInputStream(), multipartFile.getSize(),
                getLoggedInUser());
    }

    @Operation(summary = "Delete an image")
    @DeleteMapping("/{uuid}")
    public void deleteImage(@PathVariable String uuid) throws ElementNotFound, NotAuthorizedException {
        if (!recipeImageService.hasAccessPermissionToRecipeImage(uuid, getLoggedInUser())) {
            throw new NotAuthorizedException();
        }

        try {
            recipeImageService.deleteImage(uuid);
        } catch (IOException e) {
            throw new ElementNotFound();
        }
    }
}
