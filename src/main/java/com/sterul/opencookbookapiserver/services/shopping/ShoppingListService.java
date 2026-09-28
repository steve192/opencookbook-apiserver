package com.sterul.opencookbookapiserver.services.shopping;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingList;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.repositories.ShoppingListRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import lombok.extern.slf4j.Slf4j;

/** The lists of a person or a household. Every scope has a default list, made the first time it is asked for. */
@Service
@Slf4j
@Transactional
public class ShoppingListService {

    static final int MAX_LISTS_PER_SCOPE = 20;

    private final ShoppingListRepository listRepository;

    public ShoppingListService(ShoppingListRepository listRepository) {
        this.listRepository = listRepository;
    }

    /** @param scopes every scope the caller may see, each already verified */
    public List<ShoppingList> listsIn(List<PlanScope> scopes) {
        scopes.forEach(this::defaultListIn);
        return scopes.stream().flatMap(scope -> listRepository.findAllIn(scope).stream()).toList();
    }

    public ShoppingList defaultListIn(PlanScope scope) {
        return listRepository.findDefaultIn(scope).orElseGet(() -> save(scope, null, true));
    }

    /** Somebody else's list is not found. */
    @Transactional(readOnly = true)
    public ShoppingList listIn(Long listId, PlanScope scope) {
        return listRepository.findIn(listId, scope).orElseThrow(ElementNotFound::new);
    }

    public ShoppingList create(PlanScope scope, String name) {
        if (listRepository.countIn(scope) >= MAX_LISTS_PER_SCOPE) {
            throw new ApiException(ApiErrorCode.TOO_MANY_SHOPPING_LISTS, "Shopping list limit reached");
        }
        return save(scope, name.strip(), false);
    }

    public ShoppingList rename(Long listId, PlanScope scope, String name) {
        var list = listIn(listId, scope);
        list.setName(name.strip());
        return listRepository.save(list);
    }

    public void delete(Long listId, PlanScope scope) {
        var list = listIn(listId, scope);
        if (list.isDefaultList()) {
            throw new ApiException(ApiErrorCode.CONFLICT, "The default shopping list cannot be deleted");
        }
        log.info("Deleting shopping list {}", listId);
        listRepository.delete(list);
    }

    private ShoppingList save(PlanScope scope, String name, boolean defaultList) {
        var list = ShoppingList.builder().name(name).defaultList(defaultList).build();
        scope.assignTo(list);
        return listRepository.save(list);
    }
}
