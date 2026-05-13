export type UiTheme = 'light' | 'dark';
export type UiLocale = 'en' | 'zh';

export interface UiCopy {
  brand: string;
  brandAccent: string;
  themeLight: string;
  themeDark: string;
  localeEnglish: string;
  localeChinese: string;
  login: {
    title: string;
    subtitle: string;
    account: string;
    password: string;
    deviceName: string;
    platform: string;
    submitIdle: string;
    submitBusy: string;
    environment: string;
  };
  header: {
    logout: string;
    language: string;
  };
  chat: {
    conversations: string;
    friends: string;
    search: string;
    recents: string;
    incoming: string;
    outgoing: string;
    sendRequest: string;
    requestMessage: string;
    request: string;
    accept: string;
    reject: string;
    cancel: string;
    friendChat: string;
    friendsTitle: string;
    connectedAs: string;
    offlineOperator: string;
    awaitingSession: string;
    noSelection: string;
    selectConversation: string;
    activeConversation: string;
    clear: string;
    recall: string;
    loadOlder: string;
    loadingOlder: string;
    historyLoaded: string;
    composerPlaceholder: string;
    send: string;
    typing: string;
    messageRecalled: string;
    statusSending: string;
    statusDelivered: string;
    statusRead: string;
    statusFailed: string;
    statusReceived: string;
    direct: string;
    group: string;
  };
}

const copyByLocale: Record<UiLocale, UiCopy> = {
  en: {
    brand: 'CheeseIM',
    brandAccent: 'Chat',
    themeLight: 'Light',
    themeDark: 'Dark',
    localeEnglish: 'EN',
    localeChinese: '中文',
    login: {
      title: 'Welcome back',
      subtitle: 'Sign in to CheeseIM Chat',
      account: 'Account',
      password: 'Password',
      deviceName: 'Device Name',
      platform: 'Platform',
      submitIdle: 'Sign in',
      submitBusy: 'Signing in',
      environment: 'Environment',
    },
    header: {
      logout: 'Log out',
      language: 'Language',
    },
    chat: {
      conversations: 'Conversations',
      friends: 'Friends',
      search: 'Search conversations',
      recents: 'Recents',
      incoming: 'Incoming',
      outgoing: 'Outgoing',
      sendRequest: 'Send request',
      requestMessage: 'Request message',
      request: 'Request',
      accept: 'Accept',
      reject: 'Reject',
      cancel: 'Cancel',
      friendChat: 'Chat',
      friendsTitle: 'Friends',
      connectedAs: 'Connected As',
      offlineOperator: 'Offline User',
      awaitingSession: 'Awaiting session',
      noSelection: 'No selection',
      selectConversation: 'Select a conversation',
      activeConversation: 'Active Conversation',
      clear: 'Clear',
      recall: 'Recall',
      loadOlder: 'Load older',
      loadingOlder: 'Loading older…',
      historyLoaded: 'History fully loaded',
      composerPlaceholder: 'Write a message…',
      send: 'Send',
      typing: 'Typing…',
      messageRecalled: 'Message recalled',
      statusSending: 'Sending',
      statusDelivered: 'Delivered',
      statusRead: 'Read',
      statusFailed: 'Failed',
      statusReceived: 'Received',
      direct: 'Direct',
      group: 'Group',
    },
  },
  zh: {
    brand: 'CheeseIM',
    brandAccent: 'Chat',
    themeLight: '明亮',
    themeDark: '暗夜',
    localeEnglish: 'EN',
    localeChinese: '中文',
    login: {
      title: '欢迎回来',
      subtitle: '登录 CheeseIM Chat',
      account: '账号',
      password: '密码',
      deviceName: '设备名称',
      platform: '平台',
      submitIdle: '登录',
      submitBusy: '登录中',
      environment: '环境',
    },
    header: {
      logout: '退出登录',
      language: '语言',
    },
    chat: {
      conversations: '会话',
      friends: '好友',
      search: '搜索会话',
      recents: '最近会话',
      incoming: '收到的请求',
      outgoing: '发出的请求',
      sendRequest: '发送好友请求',
      requestMessage: '请求备注',
      request: '发送请求',
      accept: '接受',
      reject: '拒绝',
      cancel: '取消',
      friendChat: '聊天',
      friendsTitle: '好友',
      connectedAs: '当前用户',
      offlineOperator: '离线用户',
      awaitingSession: '等待会话',
      noSelection: '未选择会话',
      selectConversation: '请选择一个会话',
      activeConversation: '当前会话',
      clear: '清空',
      recall: '撤回',
      loadOlder: '加载更早消息',
      loadingOlder: '加载中…',
      historyLoaded: '历史消息已全部加载',
      composerPlaceholder: '输入消息…',
      send: '发送',
      typing: '对方正在输入…',
      messageRecalled: '消息已撤回',
      statusSending: '发送中',
      statusDelivered: '已送达',
      statusRead: '已读',
      statusFailed: '发送失败',
      statusReceived: '已接收',
      direct: '单聊',
      group: '群聊',
    },
  },
};

const sessionTextMap: Record<string, { en: string; zh: string }> = {
  Offline: { en: 'Offline', zh: '离线' },
  'Restoring session': { en: 'Restoring session', zh: '恢复会话中' },
  'Reissuing ws ticket': { en: 'Reissuing ws ticket', zh: '重新签发 WS 票据' },
  'Signing in': { en: 'Signing in', zh: '登录中' },
  'Session ready': { en: 'Session ready', zh: '会话已就绪' },
  'Issuing ws ticket': { en: 'Issuing ws ticket', zh: '签发 WS 票据中' },
  Connecting: { en: 'Connecting', zh: '连接中' },
  'WS ticket issued': { en: 'WS ticket issued', zh: 'WS 票据已签发' },
  Connected: { en: 'Connected', zh: '已连接' },
  Reconnecting: { en: 'Reconnecting', zh: '重连中' },
  'Session revoked': { en: 'Session revoked', zh: '会话已失效' },
  'Connection failed': { en: 'Connection failed', zh: '连接失败' },
  'Mock Gateway': { en: 'Mock Gateway', zh: '模拟网关' },
  'PostOffice WebSocket': { en: 'PostOffice WebSocket', zh: 'PostOffice WebSocket' },
  'Fake Auth · Fake Gateway · Local Mode': { en: 'Fake Auth · Fake Gateway · Local Mode', zh: 'Fake Auth · Fake Gateway · 本地模式' },
  'AuthCenter · Social · PostOffice · Postbox · Live Mode': {
    en: 'AuthCenter · Social · PostOffice · Postbox · Live Mode',
    zh: 'AuthCenter · Social · PostOffice · Postbox · 实时模式',
  },
  'Awaiting login': { en: 'Awaiting login', zh: '等待登录' },
};

export function getUiCopy(locale: UiLocale): UiCopy {
  return copyByLocale[locale];
}

export function translateSessionText(text: string | undefined, locale: UiLocale): string {
  if (text == null || text === '') {
    return '';
  }
  return sessionTextMap[text]?.[locale] ?? text;
}
