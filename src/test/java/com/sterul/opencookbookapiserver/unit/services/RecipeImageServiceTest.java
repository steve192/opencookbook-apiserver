package com.sterul.opencookbookapiserver.unit.services;

import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.services.IllegalFiletypeException;
import com.sterul.opencookbookapiserver.services.RecipeImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@ActiveProfiles("test")
class RecipeImageServiceTest {

    @Autowired
    private RecipeImageService cut;

    @Autowired
    private ResourceLoader resourceLoader;

    private File jpgFile;
    private File pngFile;
    private File invalidFile;

    @Mock
    private User testUser;

    @BeforeEach
    void setup() throws IOException {
        jpgFile = resourceLoader.getResource("classpath:testimages/jpg_image").getFile();
        pngFile = resourceLoader.getResource("classpath:testimages/png_image").getFile();
        invalidFile = resourceLoader.getResource("classpath:testimages/invalid").getFile();

    }

    @Test
    @Transactional
    void jpegCanBeUploaded() throws IOException, IllegalFiletypeException {
        var image = cut.saveNewImage(new FileInputStream(jpgFile), 100, testUser);
        assertFileWasWritten(image);
    }

    @Test
    @Transactional
    void pngCanBeUploaded() throws IOException, IllegalFiletypeException {
        var image = cut.saveNewImage(new FileInputStream(pngFile), 100, testUser);
        assertFileWasWritten(image);
    }

    @Test
    @Transactional
    void invalidFileCannotBeUploaded() throws FileNotFoundException {
        var file = new FileInputStream(invalidFile);
        try {
            cut.saveNewImage(file, 100, testUser);
        } catch (IllegalFiletypeException e) {
            return;
        } catch (IOException e) {
            fail();
        }
        fail();
    }

    @Test
    @Transactional
    void thumbnailIsGeneratedAndSmaller() throws IOException, IllegalFiletypeException {
        var image = cut.saveNewImage(new FileInputStream(pngFile), 100, testUser);
        assertFileWasWritten(image);
        assertThumbnailFileIsSmaller(image);
    }

    private void assertThumbnailFileIsSmaller(RecipeImage image) throws IOException {
        assertTrue(cut.getImage(image.getUuid()).length
                > cut.getThumbnailImage(image.getUuid()).length);
    }

    void assertFileWasWritten(RecipeImage recipeImage) throws IOException {
        assertTrue(cut.getImage(recipeImage.getUuid()).length > 0);
    }

}
