package com.cheeseocean.im.business.conversation.service;

import com.cheeseocean.im.business.conversation.ConversationApplication;
import com.cheeseocean.im.business.conversation.api.ConversationService;
import com.cheeseocean.im.business.conversation.api.param.Conversation;
import com.cheeseocean.im.common.entity.conversation.GetAllConversationsReq;
import com.cheeseocean.im.common.entity.conversation.GetAllConversationsResp;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 会话服务测试类
 * 
 * @author CheeseIM
 */
@SpringBootTest(classes = ConversationApplication.class)
@ActiveProfiles("test")
@Disabled("Legacy Spring Boot integration test is blocked by the module's Spring/Dubbo dependency skew")
public class ConversationServiceTest {
    
    @Autowired
    private ConversationService conversationService;
    
    @Test
    public void testCreateSingleConversation() {
        String userID = "user001";
        String friendUserID = "user002";
        
        Conversation conversation = conversationService.createSingleConversation(userID, friendUserID);
        
        assertNotNull(conversation);
        assertEquals(userID, conversation.getUserID());
        assertNotNull(conversation.getConversationID());
        assertTrue(conversation.getConversationID().startsWith("single_"));
    }
    
    @Test
    public void testCreateGroupConversation() {
        String userID = "user001";
        String groupID = "group001";
        
        Conversation conversation = conversationService.createGroupConversation(userID, groupID);
        
        assertNotNull(conversation);
        assertEquals(userID, conversation.getUserID());
        assertEquals(groupID, conversation.getGroupID());
        assertNotNull(conversation.getConversationID());
        assertTrue(conversation.getConversationID().startsWith("group_"));
    }
    
    @Test
    public void testGetAllConversations() {
        String userID = "user001";
        
        // 先创建一些会话
        conversationService.createSingleConversation(userID, "user002");
        conversationService.createGroupConversation(userID, "group001");
        
        GetAllConversationsReq request = new GetAllConversationsReq(userID, "test_operation");
        GetAllConversationsResp response = conversationService.getAllConversations(request);
        
        assertNotNull(response);
        assertEquals(0, response.getErrCode().intValue());
        assertNotNull(response.getConversations());
        assertTrue(response.getConversations().size() >= 2);
    }
    
    @Test
    public void testMarkConversationAsRead() {
        String userID = "user001";
        String friendUserID = "user002";
        
        Conversation conversation = conversationService.createSingleConversation(userID, friendUserID);
        String conversationID = conversation.getConversationID();
        
        Boolean result = conversationService.markConversationAsRead(userID, conversationID, 10L);
        
        assertTrue(result);
    }
    
    @Test
    public void testSetConversationDraft() {
        String userID = "user001";
        String friendUserID = "user002";
        String draftText = "这是一条草稿消息";
        
        Conversation conversation = conversationService.createSingleConversation(userID, friendUserID);
        String conversationID = conversation.getConversationID();
        
        Boolean result = conversationService.setConversationDraft(userID, conversationID, draftText);
        
        assertTrue(result);
        
        // 验证草稿是否设置成功
        Conversation updated = conversationService.getConversation(userID, conversationID);
        assertEquals(draftText, updated.getDraftText());
    }
    
    @Test
    public void testDeleteConversation() {
        String userID = "user001";
        String friendUserID = "user002";
        
        Conversation conversation = conversationService.createSingleConversation(userID, friendUserID);
        String conversationID = conversation.getConversationID();
        
        Boolean result = conversationService.deleteConversation(userID, conversationID);
        
        assertTrue(result);
        
        // 验证会话是否删除成功
        Conversation deleted = conversationService.getConversation(userID, conversationID);
        assertNull(deleted);
    }
}
