package com.sterul.opencookbookapiserver.services.shopping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingStaple;
import com.sterul.opencookbookapiserver.repositories.ShoppingStapleRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

/** What a person keeps at home, learned from the lines they leave unticked when importing. */
@Service
@Transactional
public class StapleService {

    private final ShoppingStapleRepository stapleRepository;

    public StapleService(ShoppingStapleRepository stapleRepository) {
        this.stapleRepository = stapleRepository;
    }

    /** @param shown every line the import offered; a name shown twice counts as ticked if either was */
    public void observe(CookpalUser user, List<ObservedLine> shown) {
        var byKey = new LinkedHashMap<String, ObservedLine>();
        shown.forEach(line -> byKey.merge(ShoppingNames.key(line.name()), line,
                (first, second) -> first.ticked() ? first : second));
        var known = stapleRepository.findAllByUserAndNameKeyIn(user, byKey.keySet()).stream()
                .collect(Collectors.toMap(ShoppingStaple::getNameKey, Function.identity()));
        byKey.forEach((key, line) -> {
            var staple = known.computeIfAbsent(key, ignored -> ShoppingStaple.builder()
                    .user(user).nameKey(key).name(line.name().strip()).build());
            staple.observe(line.ticked());
            stapleRepository.save(staple);
        });
    }

    @Transactional(readOnly = true)
    public List<ShoppingStaple> staplesOf(CookpalUser user) {
        return stapleRepository.findAllByUserAndStapleTrueOrderByName(user);
    }

    @Transactional(readOnly = true)
    public Set<String> stapleKeysOf(CookpalUser user) {
        return stapleRepository.findAllByUserAndStapleTrueOrderByName(user).stream()
                .map(ShoppingStaple::getNameKey).collect(Collectors.toSet());
    }

    /** Starts counting again, so the line is offered ticked next time. */
    public void forget(Long stapleId, CookpalUser user) {
        stapleRepository.delete(stapleRepository.findByIdAndUser(stapleId, user).orElseThrow(ElementNotFound::new));
    }

    public record ObservedLine(String name, boolean ticked) {
    }
}
