package com.sterul.opencookbookapiserver.services.nutrition.matching;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;
import com.sterul.opencookbookapiserver.services.nutrition.catalogue.CatalogueChangedEvent;

import lombok.extern.slf4j.Slf4j;

/** Rebuilds the matcher index in the background after startup and after committed catalogue changes. */
@Component
@ConditionalOnNutritionEnabled
@Slf4j
public class CatalogueIndexUpdater {

    private final CatalogueMatcher matcher;
    private final CatalogueFoodRepository foodRepository;
    private final TransactionTemplate readOnly;
    private final TaskExecutor taskExecutor;

    public CatalogueIndexUpdater(CatalogueMatcher matcher, CatalogueFoodRepository foodRepository,
            TransactionTemplate transactionTemplate, @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.matcher = matcher;
        this.foodRepository = foodRepository;
        this.readOnly = new TransactionTemplate(transactionTemplate.getTransactionManager());
        this.readOnly.setReadOnly(true);
        this.taskExecutor = taskExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void buildAfterStartup() {
        taskExecutor.execute(this::rebuild);
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void rebuildAfter(CatalogueChangedEvent change) {
        log.info("Catalogue changed ({}), rebuilding the matcher index", change.reason());
        taskExecutor.execute(this::rebuild);
    }

    public synchronized void rebuild() {
        var foods = readOnly.execute(status -> foodRepository.findAllByRetiredFalse().stream().map(MatchableFood::of).toList());
        if (foods == null || foods.isEmpty()) {
            log.info("The catalogue is empty; the matcher stays unready until a dataset is imported");
            return;
        }
        matcher.rebuild(foods);
    }
}
