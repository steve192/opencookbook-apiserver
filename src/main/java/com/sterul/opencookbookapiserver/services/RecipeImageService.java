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

    private final Path imageUploadPath;
    private final Path thumbnailUploadPath;
    private final OpencookbookConfiguration opencookbookConfiguration;
    @Autowired
    private RecipeImageRepository recipeImageRepository;

    RecipeImageService(OpencookbookConfiguration opencookbookConfiguration) {
        this.opencookbookConfiguration = opencookbookConfiguration;
        imageUploadPath = Paths.get(opencookbookConfiguration.getUploadDir());
        if (!Files.exists(imageUploadPath)) {
            try {
                Files.createDirectories(imageUploadPath);
            } catch (IOException e) {
                log.error("Error creating file upload directory. There will be errors when images are uploaded");
            }
        }
        thumbnailUploadPath = Paths.get(opencookbookConfiguration.getThumbnailDir());
        if (!Files.exists(thumbnailUploadPath)) {
            try {
                Files.createDirectories(thumbnailUploadPath);
            } catch (IOException e) {
                log.error("Error creating file thumbnail directory. There will be errors when images are uploaded");
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

        var mainImage = scaleImage(bufferedImage, 1024);
        var thumbnailImage = scaleImage(bufferedImage, 256);
        saveAndConvertImage(mainImage, recipeImage.getUuid(), imageUploadPath);
        saveAndConvertImage(thumbnailImage, recipeImage.getUuid(), thumbnailUploadPath);

        return recipeImage;
    }

    private BufferedImage scaleImage(BufferedImage bufferedImage, int targetWidth) {

        var oldWidth = bufferedImage.getWidth();
        var oldHeight = bufferedImage.getHeight();

        var scalingFactor = (float) targetWidth / (float) oldWidth;
        var newHeight = (int) Math.floor(oldHeight * scalingFactor);
        var newWidth = (int) Math.floor(oldWidth * scalingFactor);


        var newImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        var graphic = newImage.createGraphics();
        graphic.drawImage(bufferedImage, 0, 0, newWidth, newHeight, Color.BLACK, null);
        return newImage;
    }

    private void saveAndConvertImage(BufferedImage bufferedImage, String uuid, Path uploadPath) throws IllegalFiletypeException {

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
        var path = imageUploadPath.resolve(uuid);
        return Files.readAllBytes(path);
    }

    public byte[] getThumbnailImage(String uuid) throws IOException {
        var path = thumbnailUploadPath.resolve(uuid);
        return Files.readAllBytes(path);
    }

    public void deleteImage(String uuid) throws IOException {
        recipeImageRepository.deleteById(uuid);
        Files.delete(imageUploadPath.resolve(uuid));
        Files.delete(thumbnailUploadPath.resolve(uuid));
    }

    public List<RecipeImage> getImagesByUser(User user) {
        return recipeImageRepository.findAllByOwner(user);
    }

}
