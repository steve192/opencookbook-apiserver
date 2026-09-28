package com.sterul.opencookbookapiserver.cronjobs;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.sterul.opencookbookapiserver.services.shopping.sync.ShoppingHousekeeping;

@EnableScheduling
@Configuration
public class ShoppingHousekeepingJob {

    private final ShoppingHousekeeping housekeeping;

    public ShoppingHousekeepingJob(ShoppingHousekeeping housekeeping) {
        this.housekeeping = housekeeping;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void keepListsSmall() {
        housekeeping.trimRecentlyBought();
        housekeeping.purgeTombstones();
        housekeeping.forgetAppliedOps();
    }
}
