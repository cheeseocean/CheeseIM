package ui

type LocaleName string

const (
	LocaleZhCN LocaleName = "zh-CN"
	LocaleEnUS LocaleName = "en-US"
)

type localeKey string

const (
	keyAppTitle            localeKey = "app.title"
	keyStatusLabel         localeKey = "status.label"
	keyTabChats            localeKey = "tab.chats"
	keyTabFriends          localeKey = "tab.friends"
	keyTabGroups           localeKey = "tab.groups"
	keyTabSettings         localeKey = "tab.settings"
	keyLoginTitle          localeKey = "login.title"
	keyLoginUserID         localeKey = "login.user_id"
	keyLoginAssertion      localeKey = "login.identity_assertion"
	keyLoginHint           localeKey = "login.hint"
	keyChatTitle           localeKey = "chat.title"
	keyChatSelectPrompt    localeKey = "chat.select"
	keyChatInput           localeKey = "chat.input"
	keyChatInputFocused    localeKey = "chat.input_focused"
	keyListEmpty           localeKey = "list.empty"
	keySettingsTitle       localeKey = "settings.title"
	keySettingsAPI         localeKey = "settings.api"
	keySettingsTCP         localeKey = "settings.tcp"
	keySettingsDevice      localeKey = "settings.device"
	keySettingsPlatform    localeKey = "settings.platform"
	keyHintTabs            localeKey = "hint.tabs"
	keyHintList            localeKey = "hint.list"
	keyHintInput           localeKey = "hint.input"
	keyHelpTitle           localeKey = "help.title"
	keyHelpBody            localeKey = "help.body"
	keyToastDisconnected   localeKey = "toast.disconnected"
	keyToastForcedLogout   localeKey = "toast.forced_logout"
	keyToastGapRepaired    localeKey = "toast.gap_repaired"
	keyToastNoConversation localeKey = "toast.no_conversation"
	keyOlderGroups         localeKey = "chat.older_groups"
)

var localeTable = map[LocaleName]map[localeKey]string{
	LocaleZhCN: {
		keyAppTitle:            "CheeseBox",
		keyStatusLabel:         "状态",
		keyTabChats:            "会话",
		keyTabFriends:          "好友",
		keyTabGroups:           "群组",
		keyTabSettings:         "设置",
		keyLoginTitle:          "登录",
		keyLoginUserID:         "用户 ID",
		keyLoginAssertion:      "短期登录断言",
		keyLoginHint:           "Enter 提交，Tab 切换输入框",
		keyChatTitle:           "聊天",
		keyChatSelectPrompt:    "选择一个会话开始聊天",
		keyChatInput:           "输入",
		keyChatInputFocused:    "输入（焦点）",
		keyListEmpty:           "（空）",
		keySettingsTitle:       "设置",
		keySettingsAPI:         "API",
		keySettingsTCP:         "TCP",
		keySettingsDevice:      "设备",
		keySettingsPlatform:    "平台",
		keyHintTabs:            "h/l 或左右切换标签  tab 下一区域  c/f/g/s 快速切换  ctrl+t 主题  ctrl+l 语言  ctrl+f 扩展  ? 帮助  q 退出",
		keyHintList:            "j/k 选择项目  enter 打开会话  tab 输入框  esc 标签  / 命令  ? 帮助",
		keyHintInput:           "输入消息后回车发送  /requests  /accept|reject|cancel <userId>  /chat <userId>  /delete  /revoke <serverMsgId|last> [reason]  esc 标签",
		keyHelpTitle:           "帮助",
		keyHelpBody:            "j/k 或上下移动\nenter 打开\nTab 切换焦点\nh/l 或左右切换标签\nc/f/g/s 快速切换\n/ 命令输入\nctrl+t 切换主题\nctrl+l 切换语言\nctrl+f 扩展模式\nr 重连\nq 退出",
		keyToastDisconnected:   "TCP 连接已断开",
		keyToastForcedLogout:   "当前会话已被服务端终止",
		keyToastGapRepaired:    "消息缺口已修复",
		keyToastNoConversation: "当前没有激活会话",
		keyOlderGroups:         "... %d 个更早的消息组",
	},
	LocaleEnUS: {
		keyAppTitle:            "CheeseBox",
		keyStatusLabel:         "Status",
		keyTabChats:            "Chats",
		keyTabFriends:          "Friends",
		keyTabGroups:           "Groups",
		keyTabSettings:         "Settings",
		keyLoginTitle:          "Login",
		keyLoginUserID:         "User ID",
		keyLoginAssertion:      "Short-lived identity assertion",
		keyLoginHint:           "Enter submit, Tab switch field",
		keyChatTitle:           "Chat",
		keyChatSelectPrompt:    "Select a conversation",
		keyChatInput:           "Input",
		keyChatInputFocused:    "Input (focused)",
		keyListEmpty:           "(empty)",
		keySettingsTitle:       "Settings",
		keySettingsAPI:         "API",
		keySettingsTCP:         "TCP",
		keySettingsDevice:      "Device",
		keySettingsPlatform:    "Platform",
		keyHintTabs:            "h/l or left/right switch tabs  tab next area  c/f/g/s quick switch  ctrl+t theme  ctrl+l locale  ctrl+f expand  ? help  q quit",
		keyHintList:            "j/k select item  enter open conversation  tab input  esc tabs  / command  ? help",
		keyHintInput:           "type message then enter  /requests  /accept|reject|cancel <userId>  /chat <userId>  /delete  /revoke <serverMsgId|last> [reason]  esc tabs",
		keyHelpTitle:           "Help",
		keyHelpBody:            "j/k or up/down move\nenter open\nTab switch focus\nh/l or left/right switch tabs\nc/f/g/s quick switch\n/ command input\nctrl+t toggle theme\nctrl+l toggle locale\nctrl+f expanded mode\nr reconnect\nq quit",
		keyToastDisconnected:   "tcp connection disconnected",
		keyToastForcedLogout:   "the server terminated this session",
		keyToastGapRepaired:    "message gap repaired",
		keyToastNoConversation: "no active conversation",
		keyOlderGroups:         "... %d older groups",
	},
}

func defaultLocale() LocaleName {
	return LocaleZhCN
}

func nextLocale(name LocaleName) LocaleName {
	if name == LocaleZhCN {
		return LocaleEnUS
	}
	return LocaleZhCN
}

func T(locale LocaleName, key localeKey) string {
	if table, ok := localeTable[locale]; ok {
		if value, ok := table[key]; ok {
			return value
		}
	}
	return localeTable[LocaleEnUS][key]
}
