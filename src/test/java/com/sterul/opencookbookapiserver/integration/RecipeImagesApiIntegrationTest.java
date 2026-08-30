package com.sterul.opencookbookapiserver.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

/**
 * Covers the upload over http, which the service tests skip by handing the service a stream
 * directly. A broken web client once sent the nine bytes "undefined" instead of an image and the
 * api answered 500, which read as a server fault rather than as a rejected upload.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class RecipeImagesApiIntegrationTest extends IntegrationTest {

    private static final String USER = "recipe-images-api@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setup() {
        if (userRepository.findByEmailAddress(USER) == null) {
            var user = new CookpalUser();
            user.setEmailAddress(USER);
            user.setPasswordHash("irrelevant");
            user.setActivated(true);
            userRepository.save(user);
        }
    }

    private static byte[] image(String format) throws IOException {
        var image = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, format, bytes);
        return bytes.toByteArray();
    }

    private MockMultipartFile part(byte[] content, String contentType) {
        return new MockMultipartFile("image", "image", contentType, content);
    }

    @Test
    @WithMockUser(username = USER)
    void pngIsAccepted() throws Exception {
        mockMvc.perform(multipart("/api/v1/recipes-images").file(part(image("png"), "image/png")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").isNotEmpty());
    }

    @Test
    @WithMockUser(username = USER)
    void jpegIsAccepted() throws Exception {
        mockMvc.perform(multipart("/api/v1/recipes-images").file(part(image("jpg"), "image/jpeg")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").isNotEmpty());
    }

    // What a web client sends when it mistakes a blob: url for a data: uri
    @Test
    @WithMockUser(username = USER)
    void bytesThatAreNoImageAreRejectedAsABadRequest() throws Exception {
        var notAnImage = "undefined".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/v1/recipes-images").file(part(notAnImage, "image/png")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = USER)
    void anEmptyUploadIsRejectedAsABadRequest() throws Exception {
        mockMvc.perform(multipart("/api/v1/recipes-images").file(part(new byte[0], "image/png")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadingRequiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/v1/recipes-images").file(part(image("png"), "image/png")))
                .andExpect(status().isUnauthorized());
    }
}
