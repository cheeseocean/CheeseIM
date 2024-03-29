package com.cheeseocean.im.common.constant;

public interface IMConstant {
    //group admin;
    //	OrdinaryMember = 0;
    //	GroupOwner     = 1;
    //	Administrator  = 2;
    //group application;
    //	Application      = 0;
    //	AgreeApplication = 1;
    //friend related;
    int BlackListFlag = 1;
    int ApplicationFriendFlag = 0;
    int FriendFlag = 1;
    int RefuseFriendFlag = -1;
    //Websocket Protocol;
    int WSGetNewestSeq = 1001;
    int WSPullMsgBySeqList = 1002;
    int WSSendMsg = 1003;
    int WSSendSignalMsg = 1004;
    int WSPushMsg = 2001;
    int WSKickOnlineMsg = 2002;
    int WsLogoutMsg = 2003;
    int WSDataError = 3001;
    ///ContentType;
    //UserRelated;
    int Text = 101;
    int Picture = 102;
    int Voice = 103;
    int Video = 104;
    int File = 105;
    int AtText = 106;
    int Merger = 107;
    int Card = 108;
    int Location = 109;
    int Custom = 110;
    int Revoke = 111;
    int HasReadReceipt = 112;
    int Typing = 113;
    int Quote = 114;
    int GroupHasReadReceipt = 116;
    int AdvancedText = 117;
    int AdvancedRevoke = 118;//影响前者消息;
    int CustomNotTriggerConversation = 119;
    int CustomOnlineOnly = 120;
    int Common = 200;
    int GroupMsg = 201;
    int SignalMsg = 202;
    int CustomNotification = 203;
    //SysRelated;
    int NotificationBegin = 1000;
    int DeleteMessageNotification = 1100;
    int FriendApplicationApprovedNotification = 1201; //add_friend_response;
    int FriendApplicationRejectedNotification = 1202; //add_friend_response;
    int FriendApplicationNotification = 1203; //add_friend;
    int FriendAddedNotification = 1204;
    int FriendDeletedNotification = 1205; //delete_friend;
    int FriendRemarkSetNotification = 1206; //set_friend_remark?;
    int BlackAddedNotification = 1207; //add_black;
    int BlackDeletedNotification = 1208; //remove_black;
    int ConversationOptChangeNotification = 1300; // change conversation opt;
    int UserNotificationBegin = 1301;
    int UserInfoUpdatedNotification = 1303; //SetSelfInfoTip             = 204;
    int UserNotificationEnd = 1399;
    int OANotification = 1400;
    int GroupNotificationBegin = 1500;
    int GroupCreatedNotification = 1501;
    int GroupInfoSetNotification = 1502;
    int JoinGroupApplicationNotification = 1503;
    int MemberQuitNotification = 1504;
    int GroupApplicationAcceptedNotification = 1505;
    int GroupApplicationRejectedNotification = 1506;
    int GroupOwnerTransferredNotification = 1507;
    int MemberKickedNotification = 1508;
    int MemberInvitedNotification = 1509;
    int MemberEnterNotification = 1510;
    int GroupDismissedNotification = 1511;
    int GroupMemberMutedNotification = 1512;
    int GroupMemberCancelMutedNotification = 1513;
    int GroupMutedNotification = 1514;
    int GroupCancelMutedNotification = 1515;
    int GroupMemberInfoSetNotification = 1516;
    int GroupMemberSetToAdminNotification = 1517;
    int GroupMemberSetToOrdinaryUserNotification = 1518;
    int SignalingNotificationBegin = 1600;
    int SignalingNotification = 1601;
    int SignalingNotificationEnd = 1649;
    int SuperGroupNotificationBegin = 1650;
    int SuperGroupUpdateNotification = 1651;
    int MsgDeleteNotification = 1652;
    int SuperGroupNotificationEnd = 1699;
    int ConversationPrivateChatNotification = 1701;
    int ConversationUnreadNotification = 1702;
    int OrganizationChangedNotification = 1801;
    int WorkMomentNotificationBegin = 1900;
    int WorkMomentNotification = 1901;
    int NotificationEnd = 3000;
    //status;
    int MsgNormal = 1;
    int MsgDeleted = 4;
    //MsgFrom;
    int UserMsgType = 100;
    int SysMsgType = 200;
    //SessionType;
    int SingleChatType = 1;
    int GroupChatType = 2;
    int SuperGroupChatType = 3;
    int NotificationChatType = 4;
    //token;
    int NormalToken = 0;
    int InValidToken = 1;
    int KickedToken = 2;
    int ExpiredToken = 3;
    //MultiTerminalLogin;
    //Full-end login, but the same end is mutually exclusive;
    int AllLoginButSameTermKick = 1;
    //Only one of the endpoints can log in;
    int SingleTerminalLogin = 2;
    //The web side can be online at the same time, and the other side can only log in at one end;
    int WebAndOther = 3;
    //The PC side is mutually exclusive, and the mobile side is mutually exclusive, but the web side can be online at the same time;
    int PcMobileAndWeb = 4;
    //The PC terminal can be online at the same time,but other terminal only one of the endpoints can login;
    int PCAndOther = 5;
    String OnlineStatus = "online";
    String OfflineStatus = "offline";
    String Registered = "registered";
    String UnRegistered = "unregistered";
    //MsgReceiveOpt;
    int ReceiveMessage = 0;
    int NotReceiveMessage = 1;
    int ReceiveNotNotifyMessage = 2;
    //OptionsKey;
    String IsHistory = "history";
    String IsPersistent = "persistent";
    String IsOfflinePush = "offlinePush";
    String IsUnreadCount = "unreadCount";
    String IsConversationUpdate = "conversationUpdate";
    String IsSenderSync = "senderSync";
    String IsNotPrivate = "notPrivate";
    String IsSenderConversationUpdate = "senderConversationUpdate";
    String IsSenderNotificationPush = "senderNotificationPush";
    //GroupStatus;
    int GroupOk = 0;
    int GroupBanChat = 1;
    int GroupStatusDismissed = 2;
    int GroupStatusMuted = 3;
    //GroupType;
    int NormalGroup = 0;
    int SuperGroup = 1;
    int WorkingGroup = 2;
    int GroupBaned = 3;
    int GroupBanPrivateChat = 4;
    //UserJoinGroupSource;
    int JoinByAdmin = 1;
    int JoinByInvitation = 2;
    int JoinBySearch = 3;
    int JoinByQRCode = 4;
    //Minio;
    int MinioDurationTimes = 3600;
    //Aws;
    int AwsDurationTimes = 3600;
    // verificationCode used for;
    int VerificationCodeForRegister = 1;
    int VerificationCodeForReset = 2;
    String VerificationCodeForRegisterSuffix = "_forRegister";
    String VerificationCodeForResetSuffix = "_forReset";
    //callbackCommand;
    String CallbackBeforeSendSingleMsgCommand = "callbackBeforeSendSingleMsgCommand";
    String CallbackAfterSendSingleMsgCommand = "callbackAfterSendSingleMsgCommand";
    String CallbackBeforeSendGroupMsgCommand = "callbackBeforeSendGroupMsgCommand";
    String CallbackAfterSendGroupMsgCommand = "callbackAfterSendGroupMsgCommand";
    String CallbackWordFilterCommand = "callbackWordFilterCommand";
    String CallbackUserOnlineCommand = "callbackUserOnlineCommand";
    String CallbackUserOfflineCommand = "callbackUserOfflineCommand";
    String CallbackUserKickOffCommand = "callbackUserKickOffCommand";
    String CallbackOfflinePushCommand = "callbackOfflinePushCommand";
    String CallbackOnlinePushCommand = "callbackOnlinePushCommand";
    String CallbackSuperGroupOnlinePushCommand = "callbackSuperGroupOnlinePushCommand";
    //callback actionCode;
    int ActionAllow = 0;
    int ActionForbidden = 1;
    //callback callbackHandleCode;
    int CallbackHandleSuccess = 0;
    int CallbackHandleFailed = 1;
    // minioUpload;
    int OtherType = 1;
    int VideoType = 2;
    int ImageType = 3;
    // workMoment permission;
    int WorkMomentPublic = 0;
    int WorkMomentPrivate = 1;
    int WorkMomentPermissionCanSee = 2;
    int WorkMomentPermissionCantSee = 3;
    // workMoment sdk notification type;
    int WorkMomentCommentNotification = 0;
    int WorkMomentLikeNotification = 1;
    int WorkMomentAtUserNotification = 2;
    // sendMsgStaus;
    int MsgStatusNotExist = 0;
    int MsgIsSending = 1;
    int MsgSendSuccessed = 2;
    int MsgSendFailed = 3;
}