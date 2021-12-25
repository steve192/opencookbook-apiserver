package com.sterul.opencookbookapiserver.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.repositories.RecipeImageRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RecipeImageService {

    @Autowired
    RecipeImageRepository recipeImageRepository;

    @Autowired
    OpencookbookConfiguration opencookbookConfiguration;

    public RecipeImage saveNewImage(InputStream inputStream, long expectedSize, User owner)
            throws IOException, IllegalFiletypeException {
        Path uploadPath = Paths.get(opencookbookConfiguration.getUploadDir());

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        if (expectedSize > opencookbookConfiguration.getMaxImageSize()) {
            throw new FileSizeLimitExceededException("Image too big", expectedSize,
                    opencookbookConfiguration.getMaxImageSize());
        }

        if (!isImage(inputStream)) {
            throw new IllegalFiletypeException();
        }

        var recipeImage = new RecipeImage();
        recipeImage.setOwner(owner);
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
        // var imageReader = imageReaders.next();
        // System.out.println("Potential image reader: " + imageReader.toString());
        // isImage = true;
        // }

        // return isImage;
    }

    public boolean hasAccessPermissionToRecipeImage(String imageUUID, User user) throws ElementNotFound {
        var image = recipeImageRepository.findById(imageUUID);
        if (!image.isPresent()) {
            throw new ElementNotFound();
        }
        return image.get().getOwner().getUserId().equals(user.getUserId());
    }

    public byte[] getImage(String uuid) throws IOException {
        var path = Paths.get(opencookbookConfiguration.getUploadDir()).resolve(uuid);
        return Files.readAllBytes(path);
    }

    public void deleteImage(String uuid) throws IOException {
        recipeImageRepository.deleteById(uuid);
        Path uploadPath = Paths.get(opencookbookConfiguration.getUploadDir());

        Files.delete(uploadPath.resolve(uuid));
    }
}
