package com.sterul.opencookbookapiserver.services.shopping.tiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingTile;
import com.sterul.opencookbookapiserver.repositories.ShoppingTileRepository;
import com.sterul.opencookbookapiserver.services.catalogue.CatalogueFacts;
import com.sterul.opencookbookapiserver.services.catalogue.dataset.CatalogueDatasetReader;

/** The catalogue foods a shopping list offers to tap. */
@Service
public class ShoppingTileService implements CatalogueFacts {

    static final String DATASET_FILE = "shopping-tiles.json";

    private final ShoppingTileRepository repository;
    private final CatalogueDatasetReader dataset;

    public ShoppingTileService(ShoppingTileRepository repository, CatalogueDatasetReader dataset) {
        this.repository = repository;
        this.dataset = dataset;
    }

    @Transactional(readOnly = true)
    public List<ShoppingTile> tiles() {
        return repository.findAllWithNames();
    }

    @Override
    @Transactional
    public void apply(Map<String, CatalogueFood> foods, List<String> report) {
        repository.deleteAllInBatch();
        var shipped = dataset.read(DATASET_FILE, Dataset.class).tiles();
        var rows = shipped.stream()
                .filter(tile -> foods.containsKey(tile.food()))
                .map(tile -> ShoppingTile.builder().food(foods.get(tile.food())).names(new HashMap<>(tile.names()))
                        .build())
                .toList();
        repository.saveAll(rows);
        report.add(CatalogueFacts.reportLine("shopping tiles", rows.size(), shipped.size()));
    }

    record Dataset(List<Tile> tiles) {
    }

    /** @param names per language, the word the tile shows */
    record Tile(String food, Map<String, String> names) {
    }
}
