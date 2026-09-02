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
        var thumbnailWidth = opencookbookConfiguration.getImageThumbnailScaleWidth();
        var originalImage = readNoLargerThan(imageUploadPath.resolve(uuid).toFile(), thumbnailWidth);
        if (originalImage == null) {
            throw new IOException("Stored image " + uuid + " can no longer be decoded");
        }
        saveAndConvertImage(scaleImage(originalImage, thumbnailWidth), uuid, thumbnailUploadPath);
    }

    /**
     * Decodes an image no larger than it has to be.
     *
     * ImageIO.read always decodes at full resolution, so a 24 megapixel photo claims about 92 MB
     * of heap before anything gets scaled down - more than this service is given in total. Asking
     * the decoder to subsample lets it skip pixels while reading, which bounds the cost by the
     * size we actually want rather than by whatever the camera produced.
     *
     * The result is at least targetWidth wide whenever the source is, so the scaling afterwards
     * still only ever shrinks and no quality is invented.
     *
     * @param source anything ImageIO can open, here an InputStream or a File
     * @param targetWidth width the image is going to be scaled to afterwards
     * @return the decoded image, or null when no decoder recognises the source
     */
    private BufferedImage readNoLargerThan(Object source, int targetWidth) throws IOException {
        try (var imageInput = ImageIO.createImageInputStream(source)) {
            if (imageInput == null) {
                return null;
            }
            var readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                return null;
            }

            var reader = readers.next();
            try {
                reader.setInput(imageInput);
                var readParam = reader.getDefaultReadParam();
                var subsampling = Math.max(1, reader.getWidth(0) / targetWidth);
                readParam.setSourceSubsampling(subsampling, subsampling, 0, 0);
                return reader.read(0, readParam);
            } finally {
                reader.dispose();
            }
        }
    }

    public RecipeImage saveNewImage(InputStream inputStream, long expectedSize, CookpalUser owner)
            throws IOException, IllegalFiletypeException {
        log.info("Saving new image for user {}", owner);
        if (expectedSize > opencookbookConfiguration.getMaxImageSize()) {
            throw new FileSizeLimitExceededException("Image too big", expectedSize,
                    opencookbookConfiguration.getMaxImageSize());
        }

        var imageWidth = opencookbookConfiguration.getImageScaleWidth();
        var bufferedImage = readNoLargerThan(inputStream, imageWidth);

        if (bufferedImage == null) {
            log.warn("Uploaded image is not an image, aborting");
            throw new IllegalFiletypeException();
        }

        var recipeImage = new RecipeImage();
        recipeImage.setOwner(owner);
        recipeImage = recipeImageRepository.save(recipeImage);

        saveAndConvertImage(scaleImage(bufferedImage, imageWidth), recipeImage.getUuid(), imageUploadPath);

        // Derived from the image already in memory. Reading the just written file back in meant a
        // third decode while the full sized one was still held, which is what ran the heap out.
        var thumbnailWidth = opencookbookConfiguration.getImageThumbnailScaleWidth();
        saveAndConvertImage(scaleImage(bufferedImage, thumbnailWidth), recipeImage.getUuid(), thumbnailUploadPath);

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

    /**
     * Duplicates a stored image under a new owner. An imported recipe needs image files of its
     * own: a reference to the sharer's image would stop working the moment they deleted theirs.
     */
    public RecipeImage copyImage(String sourceUuid, CookpalUser newOwner) throws IOException {
        var copy = new RecipeImage();
        copy.setOwner(newOwner);
        copy = recipeImageRepository.save(copy);

        Files.copy(imageUploadPath.resolve(sourceUuid), imageUploadPath.resolve(copy.getUuid()));

        // A missing thumbnail is regenerated on first read, so copying it is an optimisation
        // rather than a requirement - which is why a source that never had one is not an error.
        var sourceThumbnail = thumbnailUploadPath.resolve(sourceUuid);
        if (Files.exists(sourceThumbnail)) {
            Files.copy(sourceThumbnail, thumbnailUploadPath.resolve(copy.getUuid()));
        }

        log.info("Copied image {} to {} for user {}", sourceUuid, copy.getUuid(), newOwner.getUserId());
        return copy;
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
