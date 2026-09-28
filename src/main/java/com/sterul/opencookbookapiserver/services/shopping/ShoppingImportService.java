package com.sterul.opencookbookapiserver.services.shopping;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.BringExport;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingList;
import com.sterul.opencookbookapiserver.services.BringExportService;
import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.services.shopping.StapleService.ObservedLine;
import com.sterul.opencookbookapiserver.services.shopping.sync.ShoppingOp;
import com.sterul.opencookbookapiserver.services.shopping.sync.ShoppingSyncService;

/** Where a finished import sheet goes, a list or Bring; either way the lines left out teach staples. */
@Service
@Transactional
public class ShoppingImportService {

    private final ShoppingSyncService sync;
    private final BringExportService bringExports;
    private final StapleService staples;

    public ShoppingImportService(ShoppingSyncService sync, BringExportService bringExports, StapleService staples) {
        this.sync = sync;
        this.bringExports = bringExports;
        this.staples = staples;
    }

    /** @param shown every line the sheet offered, ticked or not */
    public ShoppingList toList(Long listId, PlanScope scope, List<ShoppingOp.Add> lines, List<ObservedLine> shown,
            CookpalUser user) {
        var list = sync.importLines(listId, scope, lines, user);
        staples.observe(user, shown);
        return list;
    }

    /** @param lines finished lines ("500 g Mehl") */
    public BringExport toBring(String title, int servings, List<String> lines, List<ObservedLine> shown,
            CookpalUser user) {
        staples.observe(user, shown);
        return bringExports.createBringExport(title, servings, lines, user);
    }
}
