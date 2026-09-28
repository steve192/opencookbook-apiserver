package com.sterul.opencookbookapiserver.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingStaple;

public interface ShoppingStapleRepository extends JpaRepository<ShoppingStaple, Long> {

    List<ShoppingStaple> findAllByUserAndNameKeyIn(CookpalUser user, Collection<String> nameKeys);

    List<ShoppingStaple> findAllByUserAndStapleTrueOrderByName(CookpalUser user);

    Optional<ShoppingStaple> findByIdAndUser(Long id, CookpalUser user);
}
