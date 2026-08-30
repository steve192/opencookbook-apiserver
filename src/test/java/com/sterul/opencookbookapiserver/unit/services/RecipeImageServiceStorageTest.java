package com.sterul.opencookbookapiserver.unit.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.services.RecipeImageService;

/**
 * Storage failures must be recognisable as such. They used to be swallowed at startup and then
 * reported as an illegal filetype on every upload, which pointed at the uploader's image instead
 * of at the disk.
 */
class RecipeImageServiceStorageTest {

    private OpencookbookConfiguration configurationWith(Path uploadDir, Path thumbnailDir) {
        var configuration = new OpencookbookConfiguration();
        configuration.setUploadDir(uploadDir.toString());
        configuration.setThumbnailDir(thumbnailDir.toString());
        return configuration;
    }

    @Test
    void storageDirectoriesAreCreatedOnStartup(@TempDir Path tempDir) {
        var uploadDir = tempDir.resolve("images");
        var thumbnailDir = tempDir.resolve("images/thumbnails");

        new RecipeImageService(configurationWith(uploadDir, thumbnailDir));

        assertTrue(Files.isDirectory(uploadDir));
        assertTrue(Files.isDirectory(thumbnailDir));
    }

    @Test
    void startupFailsWhenTheImageDirectoryCannotBeCreated(@TempDir Path tempDir) throws IOException {
        // A plain file where the directory should go: creating it cannot succeed
        var blockedByFile = Files.createFile(tempDir.resolve("images"));

        var thrown = assertThrows(IllegalStateException.class,
                () -> new RecipeImageService(configurationWith(blockedByFile, tempDir.resolve("thumbnails"))));

        assertTrue(thrown.getMessage().contains(blockedByFile.toAbsolutePath().toString()));
        assertInstanceOf(IOException.class, thrown.getCause());
    }

    @Test
    void startupFailsWhenTheThumbnailDirectoryCannotBeCreated(@TempDir Path tempDir) throws IOException {
        var blockedByFile = Files.createFile(tempDir.resolve("thumbnails"));

        assertThrows(IllegalStateException.class,
                () -> new RecipeImageService(configurationWith(tempDir.resolve("images"), blockedByFile)));
    }

    @Test
    void startingTwiceOverAnExistingDirectoryIsFine(@TempDir Path tempDir) {
        var configuration = configurationWith(tempDir.resolve("images"), tempDir.resolve("thumbnails"));

        new RecipeImageService(configuration);
        new RecipeImageService(configuration);

        assertEquals(true, Files.isDirectory(tempDir.resolve("images")));
    }
}
