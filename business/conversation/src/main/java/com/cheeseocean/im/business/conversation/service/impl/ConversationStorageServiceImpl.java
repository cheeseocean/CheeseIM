package com.cheeseocean.im.business.conversation.service.impl;

import com.cheeseocean.im.business.conversation.api.param.Conversation;
import com.cheeseocean.im.business.conversation.entity.ConversationMongo;
import com.cheeseocean.im.business.conversation.entity.VersionLogMongo;
import com.cheeseocean.im.business.conversation.repository.ConversationRepository;
import com.cheeseocean.im.business.conversation.repository.VersionLogRepository;
import com.cheeseocean.im.business.conversation.service.ConversationStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 会话存储服务实现类 - 严格按照OpenIM的ConversationDatabase接口实现
 * 使用Redis缓存 + MongoDB持久化的二级缓存架构
 *
 * @author CheeseIM
 */
@Service
public class ConversationStorageServiceImpl implements ConversationStorageService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationStorageServiceImpl.class);

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private VersionLogRepository versionLogRepository;

    @Autowired
    private ConversationCache conversationCache;

    @Autowired
    private MongoTemplate mongoTemplate;
    
    @Override
    public Boolean updateUsersConversationField(List<String> userIDs, String conversationID, Map<String, Object> args) {
        try {
            Query query = new Query(Criteria.where("ownerUserID").in(userIDs).and("conversationID").is(conversationID));
            Update update = new Update();
            args.forEach(update::set);

            mongoTemplate.updateMulti(query, update, ConversationMongo.class);
            logger.info("更新用户会话字段成功: userIDs={}, conversationID={}, fields={}", userIDs, conversationID, args.keySet());
            return true;
        } catch (Exception e) {
            logger.error("更新用户会话字段失败: userIDs={}, conversationID={}", userIDs, conversationID, e);
            return false;
        }
    }

    @Override
    public Boolean createConversation(List<ConversationMongo> conversations) {
        try {
            conversationRepository.saveAll(conversations);
            logger.info("创建会话成功: count={}", conversations.size());
            return true;
        } catch (Exception e) {
            logger.error("创建会话失败: count={}", conversations.size(), e);
            return false;
        }
    }

    @Override
    @Transactional
    public Boolean syncPeerUserPrivateConversationTx(List<ConversationMongo> conversations) {
        try {
            // 事务性操作：同步对等用户私聊会话
            for (ConversationMongo conversation : conversations) {
                Optional<ConversationMongo> existing = conversationRepository.findByOwnerUserIDAndConversationID(
                        conversation.getOwnerUserID(), conversation.getConversationID());

                if (existing.isPresent()) {
                    // 更新现有会话
                    ConversationMongo existingConv = existing.get();
                    existingConv.setRecvMsgOpt(conversation.getRecvMsgOpt());
                    existingConv.setIsPinned(conversation.getIsPinned());
                    existingConv.setIsPrivateChat(conversation.getIsPrivateChat());
                    existingConv.setGroupAtType(conversation.getGroupAtType());
                    existingConv.setEx(conversation.getEx());
                    existingConv.setBurnDuration(conversation.getBurnDuration());
                    existingConv.setAttachedInfo(conversation.getAttachedInfo());
                    conversationRepository.save(existingConv);
                } else {
                    // 创建新会话
                    conversationRepository.save(conversation);
                }
            }
            logger.info("同步对等用户私聊会话成功: count={}", conversations.size());
            return true;
        } catch (Exception e) {
            logger.error("同步对等用户私聊会话失败: count={}", conversations.size(), e);
            return false;
        }
    }

    @Override
    public List<Conversation> findConversations(String ownerUserID, List<String> conversationIDs) {
        try {
            // 先尝试从缓存获取
            List<Conversation> cachedConversations = new ArrayList<>();
            List<String> missedConversationIDs = new ArrayList<>();

            for (String conversationID : conversationIDs) {
                Conversation cached = conversationCache.getConversation(ownerUserID, conversationID);
                if (cached != null) {
                    cachedConversations.add(cached);
                } else {
                    missedConversationIDs.add(conversationID);
                }
            }

            // 如果有缓存未命中的，从数据库获取
            if (!missedConversationIDs.isEmpty()) {
                List<ConversationMongo> dbConversations = conversationRepository.findByOwnerUserIDAndConversationIDIn(ownerUserID, missedConversationIDs);
                // 将从数据库获取的数据转换为业务对象并放入缓存
                for (ConversationMongo conversationMongo : dbConversations) {
                    Conversation conversation = conversationMongo.toConversation();
                    conversationCache.setConversation(conversation);
                    cachedConversations.add(conversation);
                }
            }

            logger.debug("查找用户会话: ownerUserID={}, total={}, cached={}, fromDB={}",
                    ownerUserID, cachedConversations.size(), conversationIDs.size() - missedConversationIDs.size(), missedConversationIDs.size());
            return cachedConversations;
        } catch (Exception e) {
            logger.error("查找用户会话失败: ownerUserID={}, conversationIDs={}", ownerUserID, conversationIDs, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<Conversation> getUserAllConversation(String ownerUserID) {
        try {
            // 先尝试从缓存获取
            List<Conversation> cached = conversationCache.getUserConversations(ownerUserID);
            if (cached != null) {
                logger.debug("命中用户所有会话缓存: ownerUserID={}, count={}", ownerUserID, cached.size());
                return cached;
            }

            // 缓存未命中，从数据库获取
            List<ConversationMongo> conversationMongos = conversationRepository.findByOwnerUserID(ownerUserID);

            // 转换为业务对象
            List<Conversation> conversations = conversationMongos.stream()
                    .map(ConversationMongo::toConversation)
                    .collect(Collectors.toList());

            // 将结果放入缓存
            if (!conversations.isEmpty()) {
                conversationCache.setUserConversations(ownerUserID, conversations);
                // 同时缓存单个会话
                for (Conversation conversation : conversations) {
                    conversationCache.setConversation(conversation);
                }
            }

            logger.debug("从数据库获取用户所有会话: ownerUserID={}, count={}", ownerUserID, conversations.size());
            return conversations;
        } catch (Exception e) {
            logger.error("获取用户所有会话失败: ownerUserID={}", ownerUserID, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    @Transactional
    public Boolean setUserConversations(String ownerUserID, List<Conversation> conversations) {
        try {
            List<Conversation> savedConversations = new ArrayList<>();

            for (Conversation conversation : conversations) {
                conversation.setOwnerUserID(ownerUserID);

                // 转换为数据库对象
                ConversationMongo conversationMongo = ConversationMongo.fromConversation(conversation);

                Optional<ConversationMongo> existing = conversationRepository.findByOwnerUserIDAndConversationID(
                        ownerUserID, conversation.getConversationID());

                ConversationMongo savedConversationMongo;
                if (existing.isPresent()) {
                    // 更新现有会话
                    ConversationMongo existingConv = existing.get();
                    updateConversationMongoFields(existingConv, conversationMongo);
                    savedConversationMongo = conversationRepository.save(existingConv);
                } else {
                    // 创建新会话
                    savedConversationMongo = conversationRepository.save(conversationMongo);
                }

                // 转换回业务对象
                Conversation savedConversation = savedConversationMongo.toConversation();
                savedConversations.add(savedConversation);

                // 更新单个会话缓存
                conversationCache.setConversation(savedConversation);
            }

            // 清除用户会话列表缓存，因为列表已经变化
            conversationCache.deleteUserConversations(ownerUserID);
            // 清除会话ID列表缓存
            conversationCache.setUserConversationIDs(ownerUserID, null);
            // 清除会话ID哈希缓存
            conversationCache.setUserConversationIDsHash(ownerUserID, null);

            logger.info("设置用户会话成功: ownerUserID={}, count={}", ownerUserID, conversations.size());
            return true;
        } catch (Exception e) {
            logger.error("设置用户会话失败: ownerUserID={}, count={}", ownerUserID, conversations.size(), e);
            return false;
        }
    }

    @Override
    @Transactional
    public Boolean setUsersConversationFieldTx(List<String> userIDs, ConversationMongo conversation, Map<String, Object> fieldMap) {
        try {
            for (String userID : userIDs) {
                Optional<ConversationMongo> existing = conversationRepository.findByOwnerUserIDAndConversationID(
                        userID, conversation.getConversationID());

                if (existing.isPresent()) {
                    // 更新现有会话
                    ConversationMongo existingConv = existing.get();
                    updateConversationWithFieldMap(existingConv, fieldMap);
                    conversationRepository.save(existingConv);
                } else {
                    // 创建新会话
                    ConversationMongo newConv = new ConversationMongo();
                    newConv.setOwnerUserID(userID);
                    newConv.setConversationID(conversation.getConversationID());
                    newConv.setConversationType(conversation.getConversationType());
                    newConv.setUserID(conversation.getUserID());
                    newConv.setGroupID(conversation.getGroupID());
                    updateConversationWithFieldMap(newConv, fieldMap);
                    conversationRepository.save(newConv);
                }
            }
            logger.info("设置用户会话字段成功: userIDs={}, conversationID={}", userIDs, conversation.getConversationID());
            return true;
        } catch (Exception e) {
            logger.error("设置用户会话字段失败: userIDs={}, conversationID={}", userIDs, conversation.getConversationID(), e);
            return false;
        }
    }

    @Override
    public Boolean updateUserConversations(String userID, Map<String, Object> args) {
        try {
            Query query = new Query(Criteria.where("userID").is(userID));
            Update update = new Update();
            args.forEach(update::set);

            mongoTemplate.updateMulti(query, update, ConversationMongo.class);
            logger.info("更新用户相关会话成功: userID={}, fields={}", userID, args.keySet());
            return true;
        } catch (Exception e) {
            logger.error("更新用户相关会话失败: userID={}", userID, e);
            return false;
        }
    }
    
    @Override
    public Boolean createGroupChatConversation(String groupID, List<String> userIDs, ConversationMongo conversation) {
        try {
            List<ConversationMongo> conversations = new ArrayList<>();
            for (String userID : userIDs) {
                ConversationMongo conv = new ConversationMongo();
                conv.setOwnerUserID(userID);
                conv.setConversationID(conversation.getConversationID());
                conv.setConversationType(conversation.getConversationType());
                conv.setGroupID(groupID);
                conv.setRecvMsgOpt(conversation.getRecvMsgOpt());
                conv.setIsPinned(conversation.getIsPinned());
                conv.setIsPrivateChat(conversation.getIsPrivateChat());
                conv.setGroupAtType(conversation.getGroupAtType());
                conv.setEx(conversation.getEx());
                conv.setBurnDuration(conversation.getBurnDuration());
                conv.setMinSeq(conversation.getMinSeq());
                conv.setMaxSeq(conversation.getMaxSeq());
                conv.setMsgDestructTime(conversation.getMsgDestructTime());
                conv.setLatestMsgDestructTime(conversation.getLatestMsgDestructTime());
                conv.setIsMsgDestruct(conversation.getIsMsgDestruct());
                conv.setAttachedInfo(conversation.getAttachedInfo());
                conversations.add(conv);
            }

            conversationRepository.saveAll(conversations);
            logger.info("创建群聊会话成功: groupID={}, userCount={}", groupID, userIDs.size());
            return true;
        } catch (Exception e) {
            logger.error("创建群聊会话失败: groupID={}, userCount={}", groupID, userIDs.size(), e);
            return false;
        }
    }

    @Override
    public List<String> getConversationIDs(String userID) {
        try {
            // 先尝试从缓存获取
            List<String> cached = conversationCache.getUserConversationIDs(userID);
            if (cached != null) {
                logger.debug("命中用户会话ID列表缓存: userID={}, count={}", userID, cached.size());
                return cached;
            }

            // 缓存未命中，从数据库获取
            List<ConversationMongo> conversations = conversationRepository.findConversationIDsByOwnerUserID(userID);
            List<String> conversationIDs = conversations.stream()
                    .map(ConversationMongo::getConversationID)
                    .collect(Collectors.toList());

            // 将结果放入缓存
            if (!conversationIDs.isEmpty()) {
                conversationCache.setUserConversationIDs(userID, conversationIDs);
            }

            logger.debug("从数据库获取用户会话ID列表: userID={}, count={}", userID, conversationIDs.size());
            return conversationIDs;
        } catch (Exception e) {
            logger.error("获取用户会话ID失败: userID={}", userID, e);
            return new ArrayList<>();
        }
    }

    @Override
    public Long getUserConversationIDsHash(String ownerUserID) {
        try {
            // 先尝试从缓存获取
            Long cached = conversationCache.getUserConversationIDsHash(ownerUserID);
            if (cached != null) {
                logger.debug("命中用户会话ID哈希缓存: ownerUserID={}, hash={}", ownerUserID, cached);
                return cached;
            }

            // 缓存未命中，计算哈希值
            List<String> conversationIDs = getConversationIDs(ownerUserID);
            // 使用更好的哈希算法
            long hash = conversationIDs.stream()
                    .mapToLong(String::hashCode)
                    .reduce(0L, (a, b) -> a * 31 + b);

            // 将结果放入缓存
            conversationCache.setUserConversationIDsHash(ownerUserID, hash);

            logger.debug("计算用户会话ID哈希: ownerUserID={}, hash={}", ownerUserID, hash);
            return hash;
        } catch (Exception e) {
            logger.error("获取用户会话ID哈希失败: ownerUserID={}", ownerUserID, e);
            return 0L;
        }
    }

    @Override
    public List<String> getAllConversationIDs() {
        try {
            List<ConversationMongo> conversations = conversationRepository.findAllConversationIDs();
            return conversations.stream()
                    .map(ConversationMongo::getConversationID)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("获取所有会话ID失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public Long getAllConversationIDsNumber() {
        try {
            return conversationRepository.countAllConversationIDs();
        } catch (Exception e) {
            logger.error("获取所有会话ID数量失败", e);
            return 0L;
        }
    }

    @Override
    public List<String> pageConversationIDs(Integer pageNumber, Integer showNumber) {
        try {
            Pageable pageable = PageRequest.of(pageNumber - 1, showNumber);
            Page<ConversationMongo> page = conversationRepository.findAllConversationIDsWithPage(pageable);
            return page.getContent().stream()
                    .map(ConversationMongo::getConversationID)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("分页获取会话ID失败: pageNumber={}, showNumber={}", pageNumber, showNumber, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<ConversationMongo> getConversationsByConversationID(List<String> conversationIDs) {
        try {
            return conversationRepository.findByConversationIDIn(conversationIDs);
        } catch (Exception e) {
            logger.error("根据会话ID获取会话失败: conversationIDs={}", conversationIDs, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<ConversationMongo> getConversationIDsNeedDestruct() {
        try {
            return conversationRepository.findConversationsNeedDestruct();
        } catch (Exception e) {
            logger.error("获取需要销毁的会话失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<String> getConversationNotReceiveMessageUserIDs(String conversationID) {
        try {
            List<ConversationMongo> conversations = conversationRepository.findNotReceiveMessageUsers(conversationID);
            return conversations.stream()
                    .map(ConversationMongo::getOwnerUserID)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("获取不接收消息的用户ID失败: conversationID={}", conversationID, e);
            return new ArrayList<>();
        }
    }

    @Override
    public VersionLogMongo findConversationUserVersion(String userID, Long version, Integer limit) {
        try {
            // 先尝试从缓存获取
            VersionLogMongo cached = conversationCache.getVersionLog(userID, version);
            if (cached != null) {
                logger.debug("命中版本日志缓存: userID={}, version={}", userID, version);
                return cached;
            }

            // 缓存未命中，从数据库获取
            Pageable pageable = PageRequest.of(0, limit);
            List<VersionLogMongo> versionLogs = versionLogRepository.findByUserIDAndVersionGreaterThanEqual(userID, version, pageable);
            if (!versionLogs.isEmpty()) {
                VersionLogMongo versionLog = versionLogs.get(0);
                // 将结果放入缓存
                conversationCache.setVersionLog(versionLog);
                logger.debug("从数据库获取版本日志: userID={}, version={}", userID, version);
                return versionLog;
            }
            return null;
        } catch (Exception e) {
            logger.error("查找会话用户版本失败: userID={}, version={}, limit={}", userID, version, limit, e);
            return null;
        }
    }

    @Override
    public VersionLogMongo findMaxConversationUserVersionCache(String userID) {
        try {
            // 先尝试从缓存获取最大版本号
            Long maxVersion = conversationCache.getUserMaxVersion(userID);
            if (maxVersion != null) {
                // 如果有缓存的最大版本号，尝试获取对应的版本日志
                VersionLogMongo cached = conversationCache.getVersionLog(userID, maxVersion);
                if (cached != null) {
                    logger.debug("命中最大版本日志缓存: userID={}, maxVersion={}", userID, maxVersion);
                    return cached;
                }
            }

            // 缓存未命中，从数据库获取
            Optional<VersionLogMongo> versionLogOpt = versionLogRepository.findTopByUserIDOrderByVersionDesc(userID);
            if (versionLogOpt.isPresent()) {
                VersionLogMongo versionLog = versionLogOpt.get();
                // 将结果放入缓存
                conversationCache.setVersionLog(versionLog);
                conversationCache.setUserMaxVersion(userID, versionLog.getVersion());
                logger.debug("从数据库获取最大版本日志: userID={}, maxVersion={}", userID, versionLog.getVersion());
                return versionLog;
            }
            return null;
        } catch (Exception e) {
            logger.error("查找最大会话用户版本缓存失败: userID={}", userID, e);
            return null;
        }
    }

    @Override
    public ConversationPage getOwnerConversation(String ownerUserID, Integer pageNumber, Integer showNumber) {
        try {
            Pageable pageable = PageRequest.of(pageNumber - 1, showNumber);
            Page<ConversationMongo> page = conversationRepository.findByOwnerUserID(ownerUserID, pageable);
            return new ConversationPage(page.getContent(), page.getTotalElements());
        } catch (Exception e) {
            logger.error("获取用户会话分页失败: ownerUserID={}, pageNumber={}, showNumber={}", ownerUserID, pageNumber, showNumber, e);
            return new ConversationPage(new ArrayList<>(), 0L);
        }
    }

    @Override
    public List<String> getNotNotifyConversationIDs(String userID) {
        try {
            List<ConversationMongo> conversations = conversationRepository.findNotNotifyConversationIDs(userID);
            return conversations.stream()
                    .map(ConversationMongo::getConversationID)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("获取不通知会话ID失败: userID={}", userID, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<String> getPinnedConversationIDs(String userID) {
        try {
            List<ConversationMongo> conversations = conversationRepository.findPinnedConversationIDs(userID);
            return conversations.stream()
                    .map(ConversationMongo::getConversationID)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("获取置顶会话ID失败: userID={}", userID, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<ConversationMongo> findRandConversation(Long ts, Integer limit) {
        try {
            Pageable pageable = PageRequest.of(0, limit);
            return conversationRepository.findRandConversationByTimestamp(ts, pageable);
        } catch (Exception e) {
            logger.error("查找随机会话失败: ts={}, limit={}", ts, limit, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public void updateRecvMsgOpt(String userID, String conversationID, Integer recvMsgOpt) {
        try {
            Long updateTime = System.currentTimeMillis();
            conversationRepository.updateRecvMsgOpt(userID, conversationID, recvMsgOpt, updateTime);
        } catch (Exception e) {
            logger.error("更新会话接收消息选项失败: userID={}, conversationID={}, recvMsgOpt={}", 
                    userID, conversationID, recvMsgOpt, e);
            throw new RuntimeException("更新会话接收消息选项失败", e);
        }
    }
    
    @Override
    public void updateDraft(String userID, String conversationID, String draftText) {
        try {
            Long draftTextTime = System.currentTimeMillis();
            conversationRepository.updateDraft(userID, conversationID, draftText, draftTextTime);
        } catch (Exception e) {
            logger.error("更新会话草稿失败: userID={}, conversationID={}", userID, conversationID, e);
            throw new RuntimeException("更新会话草稿失败", e);
        }
    }
    
    @Override
    public void resetGroupAtType(String userID, String conversationID) {
        try {
            Long updateTime = System.currentTimeMillis();
            conversationRepository.resetGroupAtType(userID, conversationID, updateTime);
        } catch (Exception e) {
            logger.error("重置群@类型失败: userID={}, conversationID={}", userID, conversationID, e);
            throw new RuntimeException("重置群@类型失败", e);
        }
    }
    
    @Override
    public void updateMaxSeq(String userID, String conversationID, Long maxSeq) {
        try {
            Long updateTime = System.currentTimeMillis();
            conversationRepository.updateMaxSeq(userID, conversationID, maxSeq, updateTime);
        } catch (Exception e) {
            logger.error("更新会话最大序列号失败: userID={}, conversationID={}, maxSeq={}", 
                    userID, conversationID, maxSeq, e);
            throw new RuntimeException("更新会话最大序列号失败", e);
        }
    }
    
    @Override
    public void deleteConversation(String userID, String conversationID) {
        try {
            conversationRepository.deleteByUserIDAndConversationID(userID, conversationID);
        } catch (Exception e) {
            logger.error("删除会话失败: userID={}, conversationID={}", userID, conversationID, e);
            throw new RuntimeException("删除会话失败", e);
        }
    }
    
    @Override
    public void deleteAllConversations(String userID) {
        try {
            conversationRepository.deleteByUserID(userID);
        } catch (Exception e) {
            logger.error("删除用户所有会话失败: userID={}", userID, e);
            throw new RuntimeException("删除用户所有会话失败", e);
        }
    }
    
    @Override
    public boolean existsConversation(String userID, String conversationID) {
        return conversationRepository.existsByUserIDAndConversationID(userID, conversationID);
    }
    
    @Override
    public List<String> getConversationIDs(String userID) {
        List<ConversationMongo> conversations = conversationRepository.findConversationIDsByUserID(userID);
        return conversations.stream()
                .map(ConversationMongo::getConversationID)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<ConversationMongo> findByGroupID(String groupID) {
        return conversationRepository.findByGroupID(groupID);
    }
    
    @Override
    public void batchUpdateRecvMsgOpt(String userID, List<String> conversationIDs, Integer recvMsgOpt) {
        try {
            Long updateTime = System.currentTimeMillis();
            conversationRepository.batchUpdateRecvMsgOpt(userID, conversationIDs, recvMsgOpt, updateTime);
        } catch (Exception e) {
            logger.error("批量更新会话接收消息选项失败: userID={}, conversationIDs={}, recvMsgOpt={}", 
                    userID, conversationIDs, recvMsgOpt, e);
            throw new RuntimeException("批量更新会话接收消息选项失败", e);
        }
    }
    
    @Override
    public long countConversations(String userID) {
        return conversationRepository.countByUserID(userID);
    }
    
    @Override
    public long countConversationsByType(String userID, Integer conversationType) {
        return conversationRepository.countByUserIDAndConversationType(userID, conversationType);
    }
    
    @Override
    public ConversationMongo createOrUpdateConversation(ConversationMongo conversation) {
        try {
            Optional<ConversationMongo> existingOpt = findByUserIDAndConversationID(
                    conversation.getUserID(), conversation.getConversationID());
            
            if (existingOpt.isPresent()) {
                ConversationMongo existing = existingOpt.get();
                // 更新现有会话的关键字段
                existing.setShowName(conversation.getShowName());
                existing.setFaceURL(conversation.getFaceURL());
                existing.setUpdateTime(System.currentTimeMillis());
                return saveConversation(existing);
            } else {
                return saveConversation(conversation);
            }
        } catch (Exception e) {
            logger.error("创建或更新会话失败: {}", conversation.getConversationID(), e);
            throw new RuntimeException("创建或更新会话失败", e);
        }
    }
    
    /**
     * 更新ConversationMongo字段的辅助方法
     */
    private void updateConversationMongoFields(ConversationMongo target, ConversationMongo source) {
        if (source.getRecvMsgOpt() != null) {
            target.setRecvMsgOpt(source.getRecvMsgOpt());
        }
        if (source.getIsPinned() != null) {
            target.setIsPinned(source.getIsPinned());
        }
        if (source.getIsPrivateChat() != null) {
            target.setIsPrivateChat(source.getIsPrivateChat());
        }
        if (source.getGroupAtType() != null) {
            target.setGroupAtType(source.getGroupAtType());
        }
        if (source.getEx() != null) {
            target.setEx(source.getEx());
        }
        if (source.getBurnDuration() != null) {
            target.setBurnDuration(source.getBurnDuration());
        }
        if (source.getMinSeq() != null) {
            target.setMinSeq(source.getMinSeq());
        }
        if (source.getMaxSeq() != null) {
            target.setMaxSeq(source.getMaxSeq());
        }
        if (source.getMsgDestructTime() != null) {
            target.setMsgDestructTime(source.getMsgDestructTime());
        }
        if (source.getLatestMsgDestructTime() != null) {
            target.setLatestMsgDestructTime(source.getLatestMsgDestructTime());
        }
        if (source.getIsMsgDestruct() != null) {
            target.setIsMsgDestruct(source.getIsMsgDestruct());
        }
        if (source.getAttachedInfo() != null) {
            target.setAttachedInfo(source.getAttachedInfo());
        }
    }

    /**
     * 使用字段映射更新会话的辅助方法
     */
    private void updateConversationWithFieldMap(ConversationMongo conversation, Map<String, Object> fieldMap) {
        fieldMap.forEach((key, value) -> {
            switch (key) {
                case "recvMsgOpt":
                    conversation.setRecvMsgOpt((Integer) value);
                    break;
                case "isPinned":
                    conversation.setIsPinned((Boolean) value);
                    break;
                case "isPrivateChat":
                    conversation.setIsPrivateChat((Boolean) value);
                    break;
                case "groupAtType":
                    conversation.setGroupAtType((Integer) value);
                    break;
                case "ex":
                    conversation.setEx((String) value);
                    break;
                case "burnDuration":
                    conversation.setBurnDuration((Integer) value);
                    break;
                case "minSeq":
                    conversation.setMinSeq((Long) value);
                    break;
                case "maxSeq":
                    conversation.setMaxSeq((Long) value);
                    break;
                case "msgDestructTime":
                    conversation.setMsgDestructTime((Long) value);
                    break;
                case "latestMsgDestructTime":
                    conversation.setLatestMsgDestructTime((Long) value);
                    break;
                case "isMsgDestruct":
                    conversation.setIsMsgDestruct((Boolean) value);
                    break;
                case "attachedInfo":
                    conversation.setAttachedInfo((String) value);
                    break;
                default:
                    logger.warn("未知的会话字段: {}", key);
                    break;
            }
        });
    }
}
