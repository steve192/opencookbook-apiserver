package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.services.IllegalFiletypeException;
import com.sterul.opencookbookapiserver.services.RecipeImageService;

@SpringBootTest
@ActiveProfiles("integration-test")
class RecipeImageServiceTest extends IntegrationTest{

    @Autowired
    private RecipeImageService cut;

    @Autowired
    private ResourceLoader resourceLoader;

    private File jpgFile;
    private File pngFile;
    private File invalidFile;

    private final CookpalUser testUser = new CookpalUser();

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

    private static byte[] jpegOf(int width, int height) throws IOException {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y += 4) {
            for (int x = 0; x < width; x += 4) {
                image.setRGB(x, y, (x * 255 / width) << 16 | (y * 255 / height) << 8);
            }
        }
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", bytes);
        return bytes.toByteArray();
    }

    private static int widthOf(byte[] jpeg) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(jpeg)).getWidth();
    }

    /**
     * A 12 megapixel photo needs around 46 MB to decode at full resolution, and the upload used to
     * hold that alongside a scaled copy while decoding the written file a third time for the
     * thumbnail. That ran the production heap (~126 MB) out on images well inside the upload limit,
     * so the decoder has to subsample rather than read everything into memory.
     */
    @Test
    @Transactional
    void largeImageIsNotDecodedAtFullResolution() throws IOException, IllegalFiletypeException {
        var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assumeTrue(threads.isThreadAllocatedMemorySupported());
        var largePhoto = jpegOf(4000, 3000);

        var allocatedBefore = threads.getCurrentThreadAllocatedBytes();
        var image = cut.saveNewImage(new ByteArrayInputStream(largePhoto), largePhoto.length, testUser);
        var allocatedMegabytes = (threads.getCurrentThreadAllocatedBytes() - allocatedBefore) / (1024 * 1024);

        assertTrue(allocatedMegabytes < 32,
                "Storing the image allocated " + allocatedMegabytes
                        + " MB, which means it was decoded at full resolution (~46 MB) again");
        assertFileWasWritten(image);
    }

    @Test
    @Transactional
    void largeImageIsStoredAtTheConfiguredSizes() throws IOException, IllegalFiletypeException {
        var largePhoto = jpegOf(4000, 3000);

        var image = cut.saveNewImage(new ByteArrayInputStream(largePhoto), largePhoto.length, testUser);

        assertEquals(1200, widthOf(cut.getImage(image.getUuid())));
        assertEquals(512, widthOf(cut.getThumbnailImage(image.getUuid())));
    }

    private void assertThumbnailFileIsSmaller(RecipeImage image) throws IOException {
        assertTrue(cut.getImage(image.getUuid()).length
                > cut.getThumbnailImage(image.getUuid()).length);
    }

    void assertFileWasWritten(RecipeImage recipeImage) throws IOException {
        assertTrue(cut.getImage(recipeImage.getUuid()).length > 0);
    }

}
