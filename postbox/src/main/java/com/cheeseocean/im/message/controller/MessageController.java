package com.cheeseocean.im.message.controller;

import com.cheeseocean.im.common.entity.Message;
import com.cheeseocean.im.common.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息控制器
 * 提供REST API接口
 * 
 * @author CheeseIM
 */
@RestController
@RequestMapping("/api/v1/message")
public class MessageController {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageController.class);
    
    @Autowired
    private MessageService messageService;
    
    /**
     * 发送消息
     */
    @PostMapping("/send")
    public SendMsgResp sendMessage(@RequestBody SendMsgReq request) {
        logger.info("REST API - 发送消息请求: {}", request);
        return messageService.sendMsg(request);
    }
    
    /**
     * 批量发送消息
     */
    @PostMapping("/batch-send")
    public SendMsgResp[] batchSendMessage(@RequestBody SendMsgReq[] requests) {
        logger.info("REST API - 批量发送消息请求，数量: {}", requests.length);
        return messageService.batchSendMsg(requests);
    }
    
    /**
     * 获取会话消息历史
     */
    @GetMapping("/conversation/{conversationID}/history")
    public List<Message> getConversationHistory(
            @PathVariable String conversationID,
            @RequestParam(required = false) Long startSeq,
            @RequestParam(defaultValue = "20") Integer count) {
        logger.info("REST API - 获取会话消息历史: conversationID={}, startSeq={}, count={}", 
            conversationID, startSeq, count);
        return messageService.getConversationHistory(conversationID, startSeq, count);
    }
    
    /**
     * 获取单聊消息历史
     */
    @GetMapping("/single-chat/history")
    public List<Message> getSingleChatHistory(
            @RequestParam String userID1,
            @RequestParam String userID2,
            @RequestParam(required = false) Long startSeq,
            @RequestParam(defaultValue = "20") Integer count) {
        logger.info("REST API - 获取单聊消息历史: userID1={}, userID2={}, startSeq={}, count={}", 
            userID1, userID2, startSeq, count);
        return messageService.getSingleChatHistory(userID1, userID2, startSeq, count);
    }
    
    /**
     * 获取群聊消息历史
     */
    @GetMapping("/group/{groupID}/history")
    public List<Message> getGroupChatHistory(
            @PathVariable String groupID,
            @RequestParam(required = false) Long startSeq,
            @RequestParam(defaultValue = "20") Integer count) {
        logger.info("REST API - 获取群聊消息历史: groupID={}, startSeq={}, count={}", 
            groupID, startSeq, count);
        return messageService.getGroupChatHistory(groupID, startSeq, count);
    }
    
    /**
     * 搜索消息
     */
    @GetMapping("/search")
    public List<Message> searchMessages(
            @RequestParam String userID,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        logger.info("REST API - 搜索消息: userID={}, keyword={}, page={}, size={}", 
            userID, keyword, page, size);
        return messageService.searchMessages(userID, keyword, page, size);
    }
    
    /**
     * 标记消息为已读
     */
    @PostMapping("/mark-read")
    public Boolean markMessagesAsRead(
            @RequestParam String userID,
            @RequestBody List<String> serverMsgIDs) {
        logger.info("REST API - 标记消息为已读: userID={}, msgCount={}", userID, serverMsgIDs.size());
        return messageService.markMessagesAsRead(userID, serverMsgIDs);
    }
    
    /**
     * 撤回消息
     */
    @PostMapping("/revoke")
    public Boolean revokeMessage(
            @RequestParam String userID,
            @RequestParam String serverMsgID) {
        logger.info("REST API - 撤回消息: userID={}, serverMsgID={}", userID, serverMsgID);
        return messageService.revokeMessage(userID, serverMsgID);
    }
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public String health() {
        return "Message Service is running";
    }
}
