package com.sterul.opencookbookapiserver.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.repositories.RecipeImageRepository;

import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RecipeImageService {

    @Autowired
    RecipeImageRepository recipeImageRepository;

    @Value("${opencookbook.images.upload-dir}")
    private String uploadDir;

    @Value("${opencookbook.images.maximum-size}")
    private long maximumImageSize;

    public RecipeImage saveNewImage(InputStream inputStream, long expectedSize)
            throws IOException, IllegalFiletypeException {
        Path uploadPath = Paths.get(uploadDir);

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        if (expectedSize > maximumImageSize) {
            throw new FileSizeLimitExceededException("Image too big", expectedSize, maximumImageSize);
        }

        if (!isImage(inputStream)) {
            throw new IllegalFiletypeException();
        }

        var recipeImage = new RecipeImage();
        recipeImage = recipeImageRepository.save(recipeImage);

        Path filePath = uploadPath.resolve(recipeImage.getUuid());
        Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);

        return recipeImage;
    }

    private boolean isImage(InputStream inputStream) {
        // TODO: Implement correctly
        return true;

        // var isImage = false;
        // var imageReaders = ImageIO.getImageReaders(inputStream);
        // while (imageReaders.hasNext()) {
        //     var imageReader = imageReaders.next();
        //     System.out.println("Potential image reader: " + imageReader.toString());
        //     isImage = true;
        // }

        // return isImage;
    }
}
