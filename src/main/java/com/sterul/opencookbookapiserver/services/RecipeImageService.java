package com.sterul.opencookbookapiserver.services;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.imageio.ImageIO;
import javax.transaction.Transactional;

import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.repositories.RecipeImageRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional
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

    public void generateThumbnail(String uuid) throws IOException {
        log.info("Generating thumbnail for {}", uuid);
        var originalImageFile = imageUploadPath.resolve(uuid).toFile();
        var bufferedImage = ImageIO.read(originalImageFile);
        var thumbnailImage = scaleImage(bufferedImage, opencookbookConfiguration.getImageThumbnailScaleWidth());
        try {
            saveAndConvertImage(thumbnailImage, uuid, thumbnailUploadPath);
        } catch (IllegalFiletypeException e) {
            // Filetype is not expected to be illegal
            log.error("Illegal filetype while generating thumbnail");
        }
    }

    public RecipeImage saveNewImage(InputStream inputStream, long expectedSize, User owner)
            throws IOException, IllegalFiletypeException {
        log.info("Saving new image for user {}", owner);
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

        var mainImage = scaleImage(bufferedImage, opencookbookConfiguration.getImageScaleWidth());
        saveAndConvertImage(mainImage, recipeImage.getUuid(), imageUploadPath);

        generateThumbnail(recipeImage.getUuid());

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

    private void saveAndConvertImage(BufferedImage bufferedImage, String uuid, Path uploadPath)
            throws IllegalFiletypeException {

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
        if (!Files.exists(path)) {
            generateThumbnail(uuid);
        }
        return Files.readAllBytes(path);
    }

    public void deleteImage(String uuid) throws IOException {
        log.info("Deleting image {}", uuid);
        recipeImageRepository.deleteById(uuid);
        Files.delete(imageUploadPath.resolve(uuid));
        try {
            Files.delete(thumbnailUploadPath.resolve(uuid));
        } catch (IOException e) {
            log.error("Error deleting thumnail {}, ignoring", uuid, e);
        }
    }

    public List<RecipeImage> getImagesByUser(User user) {
        return recipeImageRepository.findAllByOwner(user);
    }

}
