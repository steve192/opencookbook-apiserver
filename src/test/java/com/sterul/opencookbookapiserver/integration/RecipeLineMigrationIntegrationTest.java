package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** V43 on a version 42 database. */
class RecipeLineMigrationIntegrationTest extends MigrationFromVersionTestBase {

    RecipeLineMigrationIntegrationTest() {
        super("42");
    }

    @Test
    void aLineAnOldImportGaveAmountZeroHasNone() {
        var salt = line(0f);
        var flour = line(200f);

        migrate();

        assertNull(amountOf(salt));
        assertEquals(200f, amountOf(flour));
    }

    @Test
    void aLineHasAnEmptyNote() {
        var flour = line(200f);

        migrate();

        assertEquals("", jdbc.queryForObject("SELECT note FROM ingredient_need WHERE id = ?", String.class, flour));
    }

    private long line(float amount) {
        var id = nextval("ingredient_need_seq");
        jdbc.update("INSERT INTO ingredient_need (id, amount, unit) VALUES (?, ?, 'g')", id, amount);
        return id;
    }

    private Float amountOf(long line) {
        return jdbc.queryForObject("SELECT amount FROM ingredient_need WHERE id = ?", Float.class, line);
    }
}
