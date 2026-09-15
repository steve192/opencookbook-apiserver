package com.sterul.opencookbookapiserver.entities.nutrition;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One dataset import; inserting the row is how instances agree who imports a release. */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NutritionDatasetImport {

    @Id
    private String checksum;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant finishedAt;

    @Column(columnDefinition = "text")
    private String report;

    public enum Status {
        RUNNING, DONE, FAILED
    }
}
