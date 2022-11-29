package com.sterul.opencookbookapiserver.repositories;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.account.User;

public interface RecipeImageRepository extends JpaRepository<RecipeImage, String> {

    public List<RecipeImage> findAllByOwner(User user);

    public List<RecipeImage> findAllByCreatedOnBefore(Instant createdOn);

}
