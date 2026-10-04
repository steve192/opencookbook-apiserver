package com.sterul.opencookbookapiserver.services.recipeimport;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.recipeimport.instagram.InstagramImporter;
import com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers.RecipeScrapersWebserviceImporter;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RecipeImporterFactory {

    private static final Set<String> INSTAGRAM = Set.of("instagram", "instagr");

    private final ChefkochImporter chefkochImporter;
    private final InstagramImporter instagramImporter;
    private final RecipeScrapersWebserviceImporter recipeScrapersWebserviceImporter;
    private final OpencookbookConfiguration opencookbookConfiguration;

    public RecipeImporterFactory(ChefkochImporter chefkochImporter, InstagramImporter instagramImporter,
            RecipeScrapersWebserviceImporter recipeScrapersWebserviceImporter,
            OpencookbookConfiguration opencookbookConfiguration) {
        this.chefkochImporter = chefkochImporter;
        this.instagramImporter = instagramImporter;
        this.recipeScrapersWebserviceImporter = recipeScrapersWebserviceImporter;
        this.opencookbookConfiguration = opencookbookConfiguration;
    }

    public IRecipeImporter getRecipeImporter(String url) {
        // Checked before anything is chosen, so that a link nobody could follow is refused here
        // rather than handed on to a scraper service that would fail on it in its own way.
        var site = registrableName(hostOf(url));
        if (INSTAGRAM.contains(site)) {
            // The scraper service cannot read Instagram.
            return instagramImporter;
        }

        var scraperUrl = opencookbookConfiguration.getRecipeScaperServiceUrl();
        if (scraperUrl != null && !scraperUrl.isEmpty()) {
            return recipeScrapersWebserviceImporter;
        }

        return switch (site) {
            case "chefkoch" -> chefkochImporter;
            default -> throw new ImportNotSupportedException();
        };
    }

    /**
     * The host a link points at.
     *
     * Parsed rather than split on slashes: the previous version indexed into the pieces of two
     * splits, so anything that was not exactly {@code protocol://host.tld/path} - a pasted
     * sentence, a link with a space in it, a bare hostname - threw
     * {@link ArrayIndexOutOfBoundsException} and reached the caller as a server error.
     *
     * @param url what somebody asked to import
     * @return the host it names
     * @throws ApiException when it names none
     */
    private static String hostOf(String url) {
        return Links.host(url).orElseThrow(() -> new ApiException(ApiErrorCode.IMPORT_URL_INVALID,
                "Not a url an import can be attempted from"));
    }

    /** The name a site is known by, with any subdomains and the top level domain dropped. */
    private static String registrableName(String host) {
        var labels = host.split("\\.");
        return labels.length < 2 ? host : labels[labels.length - 2];
    }

    public List<String> getAllImporter() {
        var hostlist = new LinkedList<String>();
        var importerList = Arrays.asList(chefkochImporter, instagramImporter, recipeScrapersWebserviceImporter);
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
