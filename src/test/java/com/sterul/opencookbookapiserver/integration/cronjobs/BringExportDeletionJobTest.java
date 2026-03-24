package com.sterul.opencookbookapiserver.integration.cronjobs;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.sterul.opencookbookapiserver.cronjobs.BringExportDeletionJob;
import com.sterul.opencookbookapiserver.entities.BringExport;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.integration.IntegrationTest;
import com.sterul.opencookbookapiserver.repositories.BringExportRepository;

@SpringBootTest
@ActiveProfiles("integration-test")
class BringExportDeletionJobTest extends IntegrationTest {

    @MockitoBean
    BringExportRepository bringExportRepository;

    BringExport validExport;
    BringExport invalidExport;

    @Autowired
    BringExportDeletionJob cut;

    @BeforeEach
    void setup() {
        validExport = BringExport.builder().id("valid").owner(new CookpalUser()).build();
        validExport.setCreatedOn(Instant.now());

        var expiredExportOwner = new CookpalUser();
        expiredExportOwner.setEmailAddress("expired@example.com");
        invalidExport = BringExport.builder().id("invalid").owner(expiredExportOwner).build();
        invalidExport.setCreatedOn(Instant.now().minusSeconds(10000));

        when(bringExportRepository.findAll()).thenReturn(List.of(validExport, invalidExport));
    }

    @Test
    void oldExportsAreDeleted() throws IOException {
        cut.deleteBringExports();
        verify(bringExportRepository, times(1)).delete(invalidExport);
        verify(bringExportRepository, times(0)).delete(validExport);
    }

}
