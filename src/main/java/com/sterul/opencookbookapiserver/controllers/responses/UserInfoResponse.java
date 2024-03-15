package com.sterul.opencookbookapiserver.controllers.responses;

import java.util.List;

import lombok.Data;

@Data
public class UserInfoResponse {
    String email;
    List<String> roles;
}
