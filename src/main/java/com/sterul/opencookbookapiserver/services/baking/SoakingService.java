package com.sterul.opencookbookapiserver.services.baking;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.baking.SoakingFood;
import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueFood;
import com.sterul.opencookbookapiserver.repositories.SoakingFoodRepository;
import com.sterul.opencookbookapiserver.services.catalogue.CatalogueFacts;
import com.sterul.opencookbookapiserver.services.catalogue.dataset.CatalogueDatasetReader;

/** The seeds, flakes and grains the bread tool puts into a soaker. */
@Service
public class SoakingService implements CatalogueFacts {

    static final String DATASET_FILE = "soaking.json";

    private final SoakingFoodRepository repository;
    private final CatalogueDatasetReader dataset;

    public SoakingService(SoakingFoodRepository repository, CatalogueDatasetReader dataset) {
        this.repository = repository;
        this.dataset = dataset;
    }

    @Transactional(readOnly = true)
    public List<SoakingFood> foods() {
        return repository.findAll();
    }

    @Override
    @Transactional
    public void apply(Map<String, CatalogueFood> foods, List<String> report) {
        repository.deleteAllInBatch();
        var shipped = dataset.read(DATASET_FILE, Dataset.class).foods();
        var rows = shipped.stream()
                .filter(soaking -> foods.containsKey(soaking.food()))
                .map(soaking -> SoakingFood.builder().food(foods.get(soaking.food())).absorption(soaking.absorption())
                        .build())
                .toList();
        repository.saveAll(rows);
        report.add(CatalogueFacts.reportLine("soaking foods", rows.size(), shipped.size()));
    }

    record Dataset(List<Soaking> foods) {
    }

    /** @param absorption grams of water a gram soaks up */
    record Soaking(String food, float absorption) {
    }
}
