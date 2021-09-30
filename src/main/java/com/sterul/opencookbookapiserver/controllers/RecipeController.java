package com.sterul.opencookbookapiserver.controllers;

import java.io.IOException;
import java.util.List;


import com.sterul.opencookbookapiserver.Constants;
import com.sterul.opencookbookapiserver.entities.Recipe;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.util.FileUploadUtil;

import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/recipes")
public class RecipeController {
    private final RecipeRepository recipeRepository;

    RecipeController(RecipeRepository repository) {
        this.recipeRepository = repository;
    }

    @GetMapping("")
    List<Recipe> searchRecipe(@RequestParam(required = false) String searchString) {
        if (searchString != null) {
            return recipeRepository.findByTitleIgnoreCaseContaining(searchString);
        } else {
            return recipeRepository.findAll();
        }
    }

    @PostMapping("") 
    Recipe newRecipe(@RequestBody Recipe newRecipe) {
        return recipeRepository.save(newRecipe);
    }

    @GetMapping("/{id}")
    Recipe single(@PathVariable Long id) {
        return recipeRepository.findById(id).get();
    }

    @PutMapping("/{id}")
    Recipe updateRecipe(@PathVariable Long id, @RequestBody Recipe recipeUpdate) {
        return recipeRepository.findById(id).map(recipe -> {
            recipe.setTitle(recipeUpdate.getTitle());
            return recipeRepository.save(recipe);
        }).orElseGet(() -> {
            recipeUpdate.setId(id);
            return recipeRepository.save(recipeUpdate);
        });
        
    }

    @PostMapping("/{id}/images")
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
