package com.sterul.opencookbookapiserver.services.recipeimport;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers.RecipeScrapersWebserviceImporter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
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

        if (opencookbookConfiguration.getRecipeScaperServiceUrl().length() > 0) {
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
            hostlist.addAll(importer.getSupportedHostnames());
        }

        return hostlist;
    }
}
