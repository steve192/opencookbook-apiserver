package com.sterul.opencookbookapiserver.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.account.User;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;

@Entity
@Data
public class RecipeImage {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator"
    )
    private String uuid;

    @JsonIgnore
    @ManyToOne
    private User owner;


    @Embedded
    private ChangeInformationEmbeddable changeInformationEmbeddable;


}
