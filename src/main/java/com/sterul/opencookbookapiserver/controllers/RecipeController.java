package com.sterul.opencookbookapiserver.controllers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import com.sterul.opencookbookapiserver.Constants;
import com.sterul.opencookbookapiserver.entities.Recipe;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.util.FileUploadUtil;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.micrometer.core.lang.Nullable;


@RestController
public class RecipeController {
    private final RecipeRepository recipeRepository;

    RecipeController(RecipeRepository repository) {
        this.recipeRepository = repository;
    }

    @GetMapping("/recipes")
    List<Recipe> searchRecipe(@RequestParam @Nullable String searchString) {
        if (searchString != null) {
            return recipeRepository.findByTitleIgnoreCaseContaining(searchString);
        } else {
            return recipeRepository.findAll();
        }
    }

    @PostMapping("/recipes") 
    Recipe newRecipe(@RequestBody Recipe newRecipe) {
        return recipeRepository.save(newRecipe);
    }

    @GetMapping("/recipes/{id}")
    Recipe single(@PathVariable Long id) {
        return recipeRepository.findById(id).get();
    }

    @PutMapping("/recipes/{id}")
    Recipe updateRecipe(@PathVariable Long id, @RequestBody Recipe recipeUpdate) {
        return recipeRepository.findById(id).map(recipe -> {
            recipe.setTitle(recipeUpdate.getTitle());
            return recipeRepository.save(recipe);
        }).orElseGet(() -> {
            recipeUpdate.setId(id);
            return recipeRepository.save(recipeUpdate);
        });
        
    }

    @PostMapping("/recipes/{id}/images")
    void uploadRecipeImage(@PathVariable Long id, @RequestParam("image") MultipartFile multipartFile) throws IOException {
        Recipe recipe = recipeRepository.getOne(id);

        String filename = StringUtils.cleanPath(multipartFile.getOriginalFilename());
        FileUploadUtil.saveFile(Constants.imageUploadDir, filename, multipartFile);

        List<String> imageList = recipe.getImages();
        imageList.add(filename);
        recipe.setImages(imageList);
        recipeRepository.save(recipe);
    }
}
