package com.sterul.opencookbookapiserver.controllers.ml;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.sterul.opencookbookapiserver.configurations.ml.ConditionalOnMlConfigured;
import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.controllers.ml.responses.MlJobResponse;
import com.sterul.opencookbookapiserver.controllers.ml.responses.PageEdgesResponse;
import com.sterul.opencookbookapiserver.controllers.responses.RecipeResponse;
import com.sterul.opencookbookapiserver.entities.ml.MlJob;
import com.sterul.opencookbookapiserver.entities.ml.MlJobStatus;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.ml.MlJobService;
import com.sterul.opencookbookapiserver.services.ml.MlSubsystemException;
import com.sterul.opencookbookapiserver.services.ml.RecipeOcrPayload;
import com.sterul.opencookbookapiserver.services.ml.recipeocr.RecipeOcrImportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/** Reading a recipe from photographs. */
@RestController
@ConditionalOnMlConfigured
@ConditionalOnProperty(prefix = "opencookbook.ml.recipe-ocr", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/v1/ml")
@Tag(name = "Recipe scanning", description = "Reading recipes from photographs")
@Slf4j
public class MlController extends BaseController {

    private final MlJobService mlJobService;
    private final RecipeOcrImportService recipeOcrImportService;

    public MlController(MlJobService mlJobService,
            RecipeOcrImportService recipeOcrImportService) {
        this.mlJobService = mlJobService;
        this.recipeOcrImportService = recipeOcrImportService;
    }

    @Operation(summary = "Scan a recipe",
            description = "Upload one or more photographs of one recipe. Returns a job to poll. "
                    + "The payload is optional json: "
                    + "{\"pages\":[{\"crop\":[[x,y],[x,y],[x,y],[x,y]]}],\"language\":\"de\"}, "
                    + "where corners are fractions of the page.")
    @PostMapping(value = "/recipe-ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MlJobResponse scanRecipe(
            @RequestParam("images") List<MultipartFile> images,
            @RequestParam(value = "payload", required = false) String payload,
            @RequestParam(value = "trainingConsent", defaultValue = "false")
            boolean trainingConsent) throws MlSubsystemException {

        requirePlausiblePageCount(images);
        var parsed = parse(payload, images.size());

        var job = mlJobService.submitRecipeOcr(getLoggedInUser(), images, parsed, trainingConsent);
        return toResponse(job);
    }

    @Operation(summary = "Find the page in a photograph",
            description = "Returns the four corners of the page, as fractions of the picture, "
                    + "for the app to offer as a crop. Answered immediately and costs no "
                    + "allowance. When nothing is found the whole frame comes back with "
                    + "detected=false.")
    @PostMapping(value = "/page-edges", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PageEdgesResponse detectPageEdges(
            @RequestParam("image") MultipartFile image) throws MlSubsystemException {
        return PageEdgesResponse.from(mlJobService.detectPageEdges(image));
    }

    @Operation(summary = "Check on a scan")
    @GetMapping("/jobs/{id}")
    public MlJobResponse getJob(@PathVariable String id)
            throws ElementNotFound, MlSubsystemException {
        return toResponse(mlJobService.get(getLoggedInUser(), id));
    }

    @Operation(summary = "Correct where the ingredients and the steps are",
            description = "Send the areas somebody marked and the recipe is read again against "
                    + "them. The photograph is not read a second time, so this answers at once. "
                    + "Body: {\"blocks\":{\"ingredients\":[{\"pageIndex\":0,"
                    + "\"box\":[left,top,right,bottom]}],\"steps\":null}}. A kind takes a "
                    + "list of areas, or a single area on its own, or null to say there is "
                    + "none of that in the picture; an absent key means it was not asked "
                    + "about. Corners are fractions of the page.")
    @PostMapping("/jobs/{id}/refine")
    public MlJobResponse refineJob(@PathVariable String id,
            @RequestBody(required = false) Map<String, Object> corrections)
            throws ElementNotFound, MlSubsystemException {

        return toResponse(mlJobService.refine(getLoggedInUser(), id,
                corrections == null ? Map.of() : corrections));
    }

    @Operation(summary = "Abandon a scan")
    @DeleteMapping("/jobs/{id}")
    public void cancelJob(@PathVariable String id) throws ElementNotFound, MlSubsystemException {
        mlJobService.cancel(getLoggedInUser(), id);
    }

    @Operation(summary = "Delete the photographs you donated for improving recognition",
            description = "Withdraws consent. The scans themselves stay in your cookbook; the "
                    + "photographs held by the subsystem are deleted.")
    @DeleteMapping("/training-data")
    public void deleteTrainingData() throws MlSubsystemException {
        var deleted = mlJobService.deleteTrainingData(getLoggedInUser());
        log.info("Deleted {} donated image(s) at a user's request", deleted);
    }

    private MlJobResponse toResponse(MlJob job) throws MlSubsystemException {
        if (job.getStatus() != MlJobStatus.COMPLETED || job.getResult() == null) {
            return MlJobResponse.of(job, null, null, null);
        }
        var result = recipeOcrImportService.read(job.getResult());
        var recipe = recipeOcrImportService.toRecipe(result, job.getOwner());
        return MlJobResponse.of(
                job, RecipeResponse.fromEntity(recipe), result.getBlocks(), result.getPhoto());
    }

    private void requirePlausiblePageCount(List<MultipartFile> images) {
        var maxPages = mlJobService.maxPagesPerRecipe();
        if (images == null || images.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one photograph is needed");
        }
        if (images.size() > maxPages) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A recipe may span at most " + maxPages + " photographs");
        }
    }

    private RecipeOcrPayload parse(String payload, int imageCount) {
        try {
            return RecipeOcrPayload.parse(payload, imageCount);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }
}
