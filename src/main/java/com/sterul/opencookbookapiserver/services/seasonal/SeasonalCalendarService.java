package com.sterul.opencookbookapiserver.services.seasonal;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.seasonal.SeasonCategory;
import com.sterul.opencookbookapiserver.entities.seasonal.SeasonMonth;
import com.sterul.opencookbookapiserver.entities.seasonal.SeasonalProduce;
import com.sterul.opencookbookapiserver.entities.seasonal.StorageFacts;
import com.sterul.opencookbookapiserver.repositories.SeasonalProduceRepository;
import com.sterul.opencookbookapiserver.services.catalogue.CatalogueFacts;
import com.sterul.opencookbookapiserver.services.catalogue.dataset.CatalogueDatasetReader;

/** The fruit and vegetables with a season, for the seasonal calendar and seasonal recipe suggestions. */
@Service
public class SeasonalCalendarService implements CatalogueFacts {

    /** The only region with seasons so far. */
    public static final String GERMANY = "DE";

    static final String DATASET_FILE = "seasonal.json";

    private final SeasonalProduceRepository repository;
    private final CatalogueDatasetReader dataset;

    public SeasonalCalendarService(SeasonalProduceRepository repository, CatalogueDatasetReader dataset) {
        this.repository = repository;
        this.dataset = dataset;
    }

    @Transactional(readOnly = true)
    public List<SeasonalProduce> produce(String region) {
        return repository.findDistinctByMonthsRegion(region);
    }

    @Override
    @Transactional
    public void apply(Map<String, CatalogueFood> foods, List<String> report) {
        repository.deleteAllInBatch();
        var shipped = dataset.read(DATASET_FILE, Dataset.class).produce();
        var rows = shipped.stream()
                .filter(produce -> foods.containsKey(produce.food()))
                .map(produce -> produce.toEntity(foods.get(produce.food())))
                .toList();
        repository.saveAll(rows);
        report.add(CatalogueFacts.reportLine("seasonal produce", rows.size(), shipped.size()));
    }

    record Dataset(List<Produce> produce) {
    }

    /** @param seasons region -> month 1 to 12 -> how the food comes to market then */
    record Produce(String food, SeasonCategory category,
            Map<String, Map<Integer, List<SeasonMonth.GrowingMethod>>> seasons, Storage storage) {

        SeasonalProduce toEntity(CatalogueFood catalogueFood) {
            var produce = SeasonalProduce.builder().food(catalogueFood).category(category)
                    .storage(storage == null ? new StorageFacts() : storage.toFacts()).build();
            seasons.forEach((region, months) -> months.forEach((month, methods) -> methods.forEach(method ->
                    produce.getMonths().add(new SeasonMonth(region, month.shortValue(), method)))));
            return produce;
        }
    }

    record Storage(StorageFacts.Place place, boolean coldSensitive, boolean ethyleneProducer,
            boolean ethyleneSensitive) {

        StorageFacts toFacts() {
            return new StorageFacts(place, coldSensitive, ethyleneProducer, ethyleneSensitive);
        }
    }
}
