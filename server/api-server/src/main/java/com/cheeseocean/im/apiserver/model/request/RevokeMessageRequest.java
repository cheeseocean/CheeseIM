package com.cheeseocean.im.apiserver.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** HTTP 撤回消息请求。 */
@Data
public class RevokeMessageRequest {

    @NotBlank(message = "serverMsgId 不能为空")
    private String serverMsgId;

    private String reason;
}
