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

import jakarta.transaction.Transactional;

import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
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

    public RecipeImageService(OpencookbookConfiguration opencookbookConfiguration) {
        this.opencookbookConfiguration = opencookbookConfiguration;
        imageUploadPath = prepareStorageDirectory(opencookbookConfiguration.getUploadDir());
        thumbnailUploadPath = prepareStorageDirectory(opencookbookConfiguration.getThumbnailDir());
    }

    /**
     * Storage that cannot be written makes every future upload fail, so a directory we cannot
     * create is a misconfiguration worth refusing to start over. Logging and carrying on only
     * moves the failure to each individual upload, where it is far harder to recognise.
     *
     * @param configuredPath configured directory, created including missing parents
     * @return the prepared directory
     */
    private Path prepareStorageDirectory(String configuredPath) {
        var path = Paths.get(configuredPath);
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot create image storage directory " + path.toAbsolutePath(), e);
        }
        return path;
    }

    public void generateThumbnail(String uuid) throws IOException {
        log.info("Generating thumbnail for {}", uuid);
        var originalImageFile = imageUploadPath.resolve(uuid).toFile();
        var bufferedImage = ImageIO.read(originalImageFile);
        var thumbnailImage = scaleImage(bufferedImage, opencookbookConfiguration.getImageThumbnailScaleWidth());
        saveAndConvertImage(thumbnailImage, uuid, thumbnailUploadPath);
    }

    public RecipeImage saveNewImage(InputStream inputStream, long expectedSize, CookpalUser owner)
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

    // Whatever goes wrong while writing is our problem, not the uploader's, so it stays an
    // IOException. Reporting it as an illegal filetype told users their image was broken while
    // the real cause was a disk we could not write to.
    private void saveAndConvertImage(BufferedImage bufferedImage, String uuid, Path uploadPath)
            throws IOException {

        var imageFile = uploadPath.resolve(uuid).toFile();

        try (var outputStream = new FileOutputStream(imageFile)) {
            ImageIO.write(bufferedImage, "jpg", outputStream);
        }
    }

    public boolean hasAccessPermissionToRecipeImage(String imageUUID, CookpalUser user) throws ElementNotFound {
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

    public List<RecipeImage> getImagesByUser(CookpalUser user) {
        return recipeImageRepository.findAllByOwner(user);
    }

}
