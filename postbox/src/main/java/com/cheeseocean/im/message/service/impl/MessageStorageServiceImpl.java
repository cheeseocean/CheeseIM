package com.cheeseocean.im.message.service.impl;

import com.cheeseocean.im.common.constants.MessageConstants;
import com.cheeseocean.im.common.entity.Message;
import com.cheeseocean.im.message.entity.ConversationSeq;
import com.cheeseocean.im.message.entity.MessageMongo;
import com.cheeseocean.im.message.repository.ConversationSeqRepository;
import com.cheeseocean.im.message.repository.MessageRepository;
import com.cheeseocean.im.message.service.MessageStorageService;
import com.cheeseocean.im.message.utils.ConversationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 消息存储服务实现类
 * 
 * @author CheeseIM
 */
@Service
public class MessageStorageServiceImpl implements MessageStorageService {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageStorageServiceImpl.class);
    
    @Autowired
    private MessageRepository messageRepository;
    
    @Autowired
    private ConversationSeqRepository conversationSeqRepository;
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    @Override
    @Transactional
    public MessageMongo saveMessage(Message message) {
        try {
            // 生成会话ID
            String conversationID = ConversationUtils.generateConversationID(
                message.getSessionType(), message.getSendID(), message.getRecvID(), message.getGroupID());
            
            // 生成序列号
            Long seq = generateSeq(conversationID);
            
            // 转换为MongoDB实体
            MessageMongo messageMongo = new MessageMongo();
            BeanUtils.copyProperties(message, messageMongo);
            messageMongo.setConversationID(conversationID);
            messageMongo.setSeq(seq);
            
            // 保存消息
            MessageMongo savedMessage = messageRepository.save(messageMongo);
            
            logger.info("消息保存成功: serverMsgID={}, conversationID={}, seq={}", 
                message.getServerMsgID(), conversationID, seq);
            
            return savedMessage;
            
        } catch (Exception e) {
            logger.error("保存消息失败: {}", message.getServerMsgID(), e);
            throw new RuntimeException("保存消息失败", e);
        }
    }
    
    @Override
    public Optional<MessageMongo> findByServerMsgID(String serverMsgID) {
        return messageRepository.findByServerMsgID(serverMsgID);
    }
    
    @Override
    public Optional<MessageMongo> findByClientMsgID(String clientMsgID) {
        return messageRepository.findByClientMsgID(clientMsgID);
    }
    
    @Override
    public Page<MessageMongo> getConversationHistory(String conversationID, Pageable pageable) {
        return messageRepository.findByConversationIDOrderBySeqDesc(conversationID, pageable);
    }
    
    @Override
    public List<MessageMongo> getMessagesBySeqRange(String conversationID, Long startSeq, Long endSeq) {
        return messageRepository.findByConversationIDAndSeqBetween(conversationID, startSeq, endSeq);
    }
    
    @Override
    public Page<MessageMongo> getSingleChatHistory(String userID1, String userID2, Pageable pageable) {
        return messageRepository.findSingleChatMessages(userID1, userID2, pageable);
    }
    
    @Override
    public Page<MessageMongo> getGroupChatHistory(String groupID, Pageable pageable) {
        return messageRepository.findByGroupIDAndSessionTypeOrderBySendTimeDesc(
            groupID, MessageConstants.SESSION_TYPE_GROUP, pageable);
    }
    
    @Override
    public Page<MessageMongo> searchMessages(String keyword, Pageable pageable) {
        return messageRepository.findByContentContaining(keyword, pageable);
    }
    
    @Override
    public Page<MessageMongo> searchUserMessages(String userID, String keyword, Pageable pageable) {
        return messageRepository.findUserMessagesWithKeyword(userID, keyword, pageable);
    }
    
    @Override
    public Optional<MessageMongo> getLatestMessage(String conversationID) {
        return messageRepository.findLatestMessageByConversationID(conversationID);
    }
    
    @Override
    public Long getMaxSeq(String conversationID) {
        Optional<ConversationSeq> seqOpt = conversationSeqRepository.findByConversationID(conversationID);
        return seqOpt.map(ConversationSeq::getMaxSeq).orElse(0L);
    }
    
    @Override
    @Transactional
    public Long generateSeq(String conversationID) {
        Optional<ConversationSeq> seqOpt = conversationSeqRepository.findByConversationID(conversationID);
        
        if (seqOpt.isPresent()) {
            ConversationSeq conversationSeq = seqOpt.get();
            Long newSeq = conversationSeq.incrementSeq();
            conversationSeqRepository.save(conversationSeq);
            return newSeq;
        } else {
            // 创建新的会话序列号记录
            ConversationSeq conversationSeq = new ConversationSeq(conversationID);
            Long newSeq = conversationSeq.incrementSeq();
            conversationSeqRepository.save(conversationSeq);
            return newSeq;
        }
    }
    
    @Override
    public long countConversationMessages(String conversationID) {
        return messageRepository.countByConversationID(conversationID);
    }
    
    @Override
    public long countUnreadMessages(String userID) {
        return messageRepository.countUnreadMessages(userID);
    }
    
    @Override
    public void markMessageAsRead(String serverMsgID) {
        Query query = new Query(Criteria.where("serverMsgID").is(serverMsgID));
        Update update = new Update().set("isRead", true);
        mongoTemplate.updateFirst(query, update, MessageMongo.class);
    }
    
    @Override
    public void markMessagesAsRead(List<String> serverMsgIDs) {
        Query query = new Query(Criteria.where("serverMsgID").in(serverMsgIDs));
        Update update = new Update().set("isRead", true);
        mongoTemplate.updateMulti(query, update, MessageMongo.class);
    }
    
    @Override
    public void deleteMessage(String serverMsgID) {
        messageRepository.findByServerMsgID(serverMsgID).ifPresent(messageRepository::delete);
    }
    
    @Override
    @Transactional
    public void deleteConversationMessages(String conversationID) {
        messageRepository.deleteByConversationID(conversationID);
        conversationSeqRepository.deleteByConversationID(conversationID);
    }
    
    @Override
    public void cleanExpiredMessages(Long expireTime) {
        messageRepository.deleteExpiredMessages(expireTime);
    }
}
