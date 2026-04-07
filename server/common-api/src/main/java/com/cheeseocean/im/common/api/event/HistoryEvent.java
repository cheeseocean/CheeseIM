package com.cheeseocean.im.common.api.event;

import com.cheeseocean.im.common.api.dto.message.Message;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 历史消息事件
 * @author xxxcrel
 */
@Data
public class HistoryEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String        conversationId;
    private Long          lastMaxSeq;
    private Long          beginSeq;
    private Long          endSeq;
    private List<Message> messages = new ArrayList<>();

}
