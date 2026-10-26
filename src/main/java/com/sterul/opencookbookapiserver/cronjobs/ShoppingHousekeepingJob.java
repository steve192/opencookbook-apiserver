package com.sterul.opencookbookapiserver.cronjobs;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.sterul.opencookbookapiserver.services.shopping.history.ShoppingHistory;
import com.sterul.opencookbookapiserver.services.shopping.history.ShoppingRegulars;
import com.sterul.opencookbookapiserver.services.shopping.sync.ShoppingHousekeeping;

@EnableScheduling
@Configuration
public class ShoppingHousekeepingJob {

    private final ShoppingHousekeeping housekeeping;
    private final ShoppingHistory history;
    private final ShoppingRegulars regulars;

    public ShoppingHousekeepingJob(ShoppingHousekeeping housekeeping, ShoppingHistory history,
            ShoppingRegulars regulars) {
        this.housekeeping = housekeeping;
        this.history = history;
        this.regulars = regulars;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void keepListsSmall() {
        housekeeping.trimRecentlyBought();
        housekeeping.purgeTombstones();
        housekeeping.forgetAppliedOps();
        history.forgetOld();
        regulars.forgetOld();
    }
}
