#!/bin/bash

# CheeseIM Conversation Service API 测试脚本

BASE_URL="http://localhost:8082/api/conversation"

echo "=== CheeseIM Conversation Service API 测试 ==="
echo "Base URL: $BASE_URL"
echo ""

# 测试1: 创建单聊会话
echo "1. 创建单聊会话"
echo "POST $BASE_URL/create_single_conversation"
curl -X POST "$BASE_URL/create_single_conversation?userID=user001&friendUserID=user002" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试2: 创建群聊会话
echo "2. 创建群聊会话"
echo "POST $BASE_URL/create_group_conversation"
curl -X POST "$BASE_URL/create_group_conversation?userID=user001&groupID=group001" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试3: 获取用户所有会话
echo "3. 获取用户所有会话"
echo "POST $BASE_URL/get_all_conversations"
curl -X POST "$BASE_URL/get_all_conversations" \
  -H "Content-Type: application/json" \
  -d '{"userID":"user001","operationID":"test_op_001"}' \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试4: 获取指定会话信息
echo "4. 获取指定会话信息"
echo "GET $BASE_URL/get_conversation"
curl -X GET "$BASE_URL/get_conversation?userID=user001&conversationID=single_user001_user002" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试5: 设置会话置顶
echo "5. 设置会话置顶"
echo "POST $BASE_URL/set_conversation"
curl -X POST "$BASE_URL/set_conversation" \
  -H "Content-Type: application/json" \
  -d '{
    "userID": "user001",
    "conversationID": "single_user001_user002",
    "isPinned": true,
    "operationID": "test_op_002"
  }' \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试6: 标记会话已读
echo "6. 标记会话已读"
echo "POST $BASE_URL/mark_conversation_as_read"
curl -X POST "$BASE_URL/mark_conversation_as_read?userID=user001&conversationID=single_user001_user002&msgSeq=10" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试7: 获取用户未读消息总数
echo "7. 获取用户未读消息总数"
echo "GET $BASE_URL/get_total_unread_msg_count"
curl -X GET "$BASE_URL/get_total_unread_msg_count?userID=user001" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试8: 设置会话草稿
echo "8. 设置会话草稿"
echo "POST $BASE_URL/set_conversation_draft"
curl -X POST "$BASE_URL/set_conversation_draft?userID=user001&conversationID=single_user001_user002&draftText=这是一条草稿消息" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试9: 获取会话ID列表
echo "9. 获取会话ID列表"
echo "GET $BASE_URL/get_conversation_ids"
curl -X GET "$BASE_URL/get_conversation_ids?userID=user001" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试10: 更新会话信息
echo "10. 更新会话信息"
echo "POST $BASE_URL/update_conversation"
curl -X POST "$BASE_URL/update_conversation?userID=user001&conversationID=single_user001_user002&latestMsg=你好，这是最新消息&latestMsgSendTime=1640995200000" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

echo "=== API 测试完成 ==="
