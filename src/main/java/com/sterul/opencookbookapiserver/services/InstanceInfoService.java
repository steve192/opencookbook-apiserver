package com.sterul.opencookbookapiserver.services;


import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
@Slf4j
public class InstanceInfoService {

    private final OpencookbookConfiguration opencookbookConfiguration;

    public InstanceInfoService(OpencookbookConfiguration opencookbookConfiguration) {
        this.opencookbookConfiguration = opencookbookConfiguration;
    }


    @Cacheable("tos")
    public String getTermsOfSerivice() {
        var path = Paths.get(opencookbookConfiguration.getTermsOfServiceFileLocation());
        try {
            return String.join("\n", Files.readAllLines(path));
        } catch (IOException e) {
            log.error("Cannot read terms of service file ", e);
            return "";
        }
    }
}
