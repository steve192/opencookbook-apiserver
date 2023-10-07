package com.sterul.opencookbookapiserver.services.recipeimport;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers.RecipeScrapersWebserviceImporter;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RecipeImporterFactory {

    @Autowired
    private ChefkochImporter chefkochImporter;

    @Autowired
    private RecipeScrapersWebserviceImporter recipeScrapersWebserviceImporter;

    @Autowired
    private OpencookbookConfiguration opencookbookConfiguration;

    public IRecipeImporter getRecipeImporter(String url) throws ImportNotSupportedException {
        var urlWithoutProtocol = url.split("//")[1];
        var domain = urlWithoutProtocol.split("/")[0];
        var domainParts = domain.split("\\.");
        var domainWithoutTLDAndSubdomains = domainParts[domainParts.length - 2];


        var scraperUrl = opencookbookConfiguration.getRecipeScaperServiceUrl();
        if (scraperUrl != null && scraperUrl.length() > 0) {
            return recipeScrapersWebserviceImporter;
        }

        switch (domainWithoutTLDAndSubdomains) {
            case "chefkoch":
                return chefkochImporter;
            default:
                throw new ImportNotSupportedException();
        }
    }

    public List<String> getAllImporter() {
        var hostlist = new LinkedList<String>();
        var importerList = Arrays.asList(chefkochImporter, recipeScrapersWebserviceImporter);
        for (var importer : importerList) {
            try {
                hostlist.addAll(importer.getSupportedHostnames());
            } catch (IOException e) {
                log.error("Error getting supported hosts from importer", e);
            }
        }

        return hostlist;
    }
}
