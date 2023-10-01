package com.sterul.opencookbookapiserver.repositoriespostgress;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.repositoriespostgress.entities.RecipeImage;
import com.sterul.opencookbookapiserver.repositoriespostgress.entities.account.CookpalUser;

public interface PGRecipeImageRepository extends JpaRepository<RecipeImage, String> {

    public List<RecipeImage> findAllByOwner(CookpalUser user);

    public List<RecipeImage> findAllByCreatedOnBefore(Instant createdOn);

}
