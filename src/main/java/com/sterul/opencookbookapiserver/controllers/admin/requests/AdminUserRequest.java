package com.sterul.opencookbookapiserver.controllers.admin.requests;

import com.sterul.opencookbookapiserver.controllers.requests.EmailAddresses;
import com.sterul.opencookbookapiserver.entities.account.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Every field replaces what was there, so all of them have to be given. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminUserRequest {

    @NotBlank
    @Email
    @Size(max = EmailAddresses.MAX_LENGTH)
    private String emailAddress;

    @NotNull
    private Boolean activated;

    /** Null means no role at all. */
    private Role role;
}
