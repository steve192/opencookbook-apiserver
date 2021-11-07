package com.sterul.opencookbookapiserver.services.recipeimport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RecipeImporterFactory {

    @Autowired
    private ChefkochImporter chefkochImporter;

    public IRecipeImporter getRecipeImporter(String url) throws ImportNotSupportedException {
        var urlWithoutProtocol = url.split("//")[1];
        var domain = urlWithoutProtocol.split("/")[0];
        var domainParts = domain.split("\\.");
        var domainWithoutTLDAndSubdomains = domainParts[domainParts.length - 2];

        switch (domainWithoutTLDAndSubdomains) {
        case "chefkoch":
            return chefkochImporter;
        default:
            throw new ImportNotSupportedException();
        }
    }
}
