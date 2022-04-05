package com.sterul.opencookbookapiserver.services;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.repositories.RecipeImageRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
@Slf4j
public class RecipeImageService {

    private final Path uploadPath;
    private final OpencookbookConfiguration opencookbookConfiguration;
    @Autowired
    private RecipeImageRepository recipeImageRepository;

    RecipeImageService(OpencookbookConfiguration opencookbookConfiguration) {
        this.opencookbookConfiguration = opencookbookConfiguration;
        uploadPath = Paths.get(opencookbookConfiguration.getUploadDir());
        if (!Files.exists(uploadPath)) {
            try {
                Files.createDirectories(uploadPath);
            } catch (IOException e) {
                log.error("Error creating file upload directory. There will be errors when images are uploaded");
            }
        }
    }

    public RecipeImage saveNewImage(InputStream inputStream, long expectedSize, User owner)
            throws IOException, IllegalFiletypeException {
        if (expectedSize > opencookbookConfiguration.getMaxImageSize()) {
            throw new FileSizeLimitExceededException("Image too big", expectedSize,
                    opencookbookConfiguration.getMaxImageSize());
        }

        var bufferedImage = ImageIO.read(inputStream);

        if (bufferedImage == null) {
            log.warn("Uploaded image is not an image, aborting");
            throw new IllegalFiletypeException();
        }

        var recipeImage = new RecipeImage();
        recipeImage.setOwner(owner);
        recipeImage = recipeImageRepository.save(recipeImage);

        bufferedImage = removeAlphaChannel(bufferedImage);
        saveAndConvertImage(bufferedImage, recipeImage.getUuid());

        return recipeImage;
    }

    private BufferedImage removeAlphaChannel(BufferedImage bufferedImage) {
        var newImage = new BufferedImage(bufferedImage.getWidth(), bufferedImage.getHeight(), BufferedImage.TYPE_INT_RGB);
        newImage.createGraphics().drawImage(bufferedImage, 0, 0, Color.BLACK, null);
        return newImage;
    }

    private void saveAndConvertImage(BufferedImage bufferedImage, String uuid) throws IOException, IllegalFiletypeException {

        var imageFile = uploadPath.resolve(uuid).toFile();

        try (var outputStream = new FileOutputStream(imageFile)) {
            ImageIO.write(bufferedImage, "jpg", outputStream);
        } catch (IOException e) {
            log.error("IO Error when converting image", e);
            throw new IllegalFiletypeException();
        }
    }


    public boolean hasAccessPermissionToRecipeImage(String imageUUID, User user) throws ElementNotFound {
        var image = recipeImageRepository.findById(imageUUID);
        if (image.isEmpty()) {
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
        Files.delete(uploadPath.resolve(uuid));
    }

    public List<RecipeImage> getImagesByUser(User user) {
        return recipeImageRepository.findAllByOwner(user);
    }

}
