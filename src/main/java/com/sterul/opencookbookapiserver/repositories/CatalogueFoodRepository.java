package com.sterul.opencookbookapiserver.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;

public interface CatalogueFoodRepository extends JpaRepository<CatalogueFood, Long> {

    Optional<CatalogueFood> findByCatalogueKey(String catalogueKey);

    List<CatalogueFood> findAllByOrigin(CatalogueFood.Origin origin);

    boolean existsByVariantOf(CatalogueFood base);

    /** With what matching needs loaded at once. */
    @EntityGraph(attributePaths = {"names", "states", "variantOf"})
    List<CatalogueFood> findAllByRetiredFalse();

    List<CatalogueFood> findAllByCatalogueKeyIn(Collection<String> catalogueKeys);

    default Map<String, CatalogueFood> findAllByCatalogueKeyMapped(Collection<String> catalogueKeys) {
        return findAllByCatalogueKeyIn(catalogueKeys).stream()
                .collect(Collectors.toMap(CatalogueFood::getCatalogueKey, Function.identity()));
    }

    @Query("select distinct name.name from CatalogueFood food join food.names name "
            + "where food.retired = false and name.languageIsoCode = :language")
    List<String> findNamesIn(@Param("language") String languageIsoCode);

    /** Case-insensitive. */
    @Query("select food from CatalogueFood food join food.names name "
            + "where name.languageIsoCode = :language and lower(name.name) = lower(:name)")
    Optional<CatalogueFood> findByName(@Param("language") String languageIsoCode, @Param("name") String name);
}
