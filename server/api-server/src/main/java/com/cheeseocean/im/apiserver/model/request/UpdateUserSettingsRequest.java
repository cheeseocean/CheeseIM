package com.cheeseocean.im.apiserver.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserSettingsRequest {
    @NotNull
    private Integer receiveOpt;
}
