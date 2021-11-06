package com.sterul.opencookbookapiserver.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.repositories.RecipeImageRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RecipeImageService {

    @Autowired
    RecipeImageRepository recipeImageRepository;

    @Value("${opencookbook.file-upload-dir}")
    private String uploadDir;

    public RecipeImage saveNewImage(InputStream inputStream) throws IOException {
        Path uploadPath = Paths.get(uploadDir);

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        var recipeImage = new RecipeImage();
        recipeImage = recipeImageRepository.save(recipeImage);

        Path filePath = uploadPath.resolve(recipeImage.getUuid());
        Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);

        return recipeImage;
    }
}
