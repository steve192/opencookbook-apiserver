package com.sterul.opencookbookapiserver.controllers.responses;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.shopping.ShoppingProvider;

import lombok.Data;

@Data
public class UserInfoResponse {
    String email;
    /** Null while the account never set one; fellow members then see a masked address. */
    String displayName;
    boolean onboarded;
    /** Null until the first shopping import asked. */
    ShoppingProvider shoppingProvider;
    List<String> roles;
}
