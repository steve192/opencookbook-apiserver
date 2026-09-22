package com.sterul.opencookbookapiserver.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

public interface RecipeImageRepository extends JpaRepository<RecipeImage, String> {

    public List<RecipeImage> findAllByOwner(CookpalUser user);

    public Optional<RecipeImage> findByUuidAndOwner(String uuid, CookpalUser owner);

    public List<RecipeImage> findAllByCreatedOnBefore(Instant createdOn);

    /** Empty while the image is uploaded but not yet attached to a recipe. */
    @Query("select recipe.owner.userId from Recipe recipe join recipe.images image where image.uuid = :uuid")
    Optional<Long> findRecipeOwnerOf(@Param("uuid") String uuid);
}
