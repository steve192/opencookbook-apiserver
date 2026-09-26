package com.sterul.opencookbookapiserver.unit.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.repositories.RecipeImageRepository;
import com.sterul.opencookbookapiserver.services.RecipeImageService;
import com.sterul.opencookbookapiserver.services.access.CookbookAccess;

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

    /** Only the storage directories are under test; the collaborators are never reached. */
    private RecipeImageService serviceWith(OpencookbookConfiguration configuration) {
        return new RecipeImageService(configuration, mock(RecipeImageRepository.class), mock(CookbookAccess.class));
    }

    @Test
    void storageDirectoriesAreCreatedOnStartup(@TempDir Path tempDir) {
        var uploadDir = tempDir.resolve("images");
        var thumbnailDir = tempDir.resolve("images/thumbnails");

        serviceWith(configurationWith(uploadDir, thumbnailDir));

        assertTrue(Files.isDirectory(uploadDir));
        assertTrue(Files.isDirectory(thumbnailDir));
    }

    @Test
    void startupFailsWhenTheImageDirectoryCannotBeCreated(@TempDir Path tempDir) throws IOException {
        // A plain file where the directory should go: creating it cannot succeed
        var blockedByFile = Files.createFile(tempDir.resolve("images"));

        var configuration = configurationWith(blockedByFile, tempDir.resolve("thumbnails"));

        var thrown = assertThrows(IllegalStateException.class, () -> serviceWith(configuration));

        assertTrue(thrown.getMessage().contains(blockedByFile.toAbsolutePath().toString()));
        assertInstanceOf(IOException.class, thrown.getCause());
    }

    @Test
    void startupFailsWhenTheThumbnailDirectoryCannotBeCreated(@TempDir Path tempDir) throws IOException {
        var blockedByFile = Files.createFile(tempDir.resolve("thumbnails"));

        var configuration = configurationWith(tempDir.resolve("images"), blockedByFile);

        assertThrows(IllegalStateException.class, () -> serviceWith(configuration));
    }

    @Test
    void startingTwiceOverAnExistingDirectoryIsFine(@TempDir Path tempDir) {
        var configuration = configurationWith(tempDir.resolve("images"), tempDir.resolve("thumbnails"));

        serviceWith(configuration);
        serviceWith(configuration);

        assertEquals(true, Files.isDirectory(tempDir.resolve("images")));
    }
}
