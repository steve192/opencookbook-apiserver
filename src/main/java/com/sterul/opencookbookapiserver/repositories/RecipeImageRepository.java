package com.sterul.opencookbookapiserver.repositories;

import com.sterul.opencookbookapiserver.entities.RecipeImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeImageRepository  extends JpaRepository<RecipeImage, String>  {
    
}
