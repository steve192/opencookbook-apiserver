package com.sterul.opencookbookapiserver.integration.cronjobs;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sterul.opencookbookapiserver.cronjobs.BringExportDeletionJob;
import com.sterul.opencookbookapiserver.entities.BringExport;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.integration.IntegrationTest;
import com.sterul.opencookbookapiserver.repositories.BringExportRepository;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public class BringExportDeletionJobTest extends IntegrationTest {

    @MockBean
    BringExportRepository bringExportRepository;

    @Mock
    BringExport validExport;
    @Mock
    BringExport invalidExport;

    @Autowired
    BringExportDeletionJob cut;

    @BeforeEach
    void setup() {
        when(validExport.getId()).thenReturn("valid");
        when(validExport.getCreatedOn()).thenReturn(Instant.now());
        when(validExport.getOwner()).thenReturn(new CookpalUser());
        
        
        when(invalidExport.getId()).thenReturn("invalid");
        when(invalidExport.getCreatedOn()).thenReturn(Instant.now().minusSeconds(10000));
        when(invalidExport.getOwner()).thenReturn(new CookpalUser());

        when(bringExportRepository.findAll()).thenReturn(List.of(validExport, invalidExport));
    }

    @Test
    void oldExportsAreDeleted() throws IOException {
        cut.deleteBringExports();
        verify(bringExportRepository, times(1)).delete(invalidExport);
        verify(bringExportRepository, times(0)).delete(validExport);
    }

}
