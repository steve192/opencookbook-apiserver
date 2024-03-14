package com.sterul.opencookbookapiserver.entities;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BringExport extends AuditableEntity {
    @Id
    @UuidGenerator
    private String id;

    @JsonIgnore
    @ManyToOne
    private CookpalUser owner;

    private int baseAmount;

    @ElementCollection
    @Column(length = 10000)
    private List<String> ingredients = new ArrayList<>();
}
