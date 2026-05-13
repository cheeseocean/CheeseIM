package ui

import (
	"strconv"
	"strings"
	"time"
)

const debugLogMaxLines = 50

type DebugLogModel struct {
	// 环形缓冲区实现滑动窗口
	buf      []debugLogEntry
	capacity int
	size     int // 当前实际日志数量
	readIdx  int // 最旧日志索引
	writeIdx int // 下一条写入位置
	enabled  bool
	width    int
	height   int
}

type debugLogEntry struct {
	time    string
	message string
	kind    debugLogKind
}

type debugLogKind int

const (
	debugLogKindInfo debugLogKind = iota
	debugLogKindSend
	debugLogKindRecv
	debugLogKindError
)

func NewDebugLogModel() *DebugLogModel {
	return &DebugLogModel{
		buf:      make([]debugLogEntry, debugLogMaxLines),
		capacity: debugLogMaxLines,
		size:     0,
		readIdx:  0,
		writeIdx: 0,
		enabled:  true,
	}
}

// appendLog 添加日志到环形缓冲区
func (m *DebugLogModel) appendLog(entry debugLogEntry) {
	if !m.enabled {
		return
	}
	m.buf[m.writeIdx] = entry
	m.writeIdx = (m.writeIdx + 1) % m.capacity
	if m.size < m.capacity {
		m.size++
	} else {
		// 缓冲区满了，最旧索引前移
		m.readIdx = (m.readIdx + 1) % m.capacity
	}
}

// getLogs 按顺序返回所有日志（最旧到最新）
func (m DebugLogModel) getLogs() []debugLogEntry {
	if m.size == 0 {
		return nil
	}
	logs := make([]debugLogEntry, m.size)
	for i := 0; i < m.size; i++ {
		logs[i] = m.buf[(m.readIdx+i)%m.capacity]
	}
	return logs
}

func (m *DebugLogModel) SetSize(width, height int) {
	m.width = width
	m.height = height
}

func (m *DebugLogModel) Toggle() {
	m.enabled = !m.enabled
}

func (m *DebugLogModel) IsEnabled() bool {
	return m.enabled
}

func (m *DebugLogModel) SetEnabled(enabled bool) {
	m.enabled = enabled
}

func (m *DebugLogModel) AppendSend(conversationID, userID, requestID, content string) {
	m.appendLog(debugLogEntry{
		time:    time.Now().Format("15:04:05.000"),
		message: formatSendLog(conversationID, userID, requestID, content),
		kind:    debugLogKindSend,
	})
}

func (m *DebugLogModel) AppendRecv(conversationID, senderID, senderName, content, clientMsgID, serverMsgID string, seq int64) {
	m.appendLog(debugLogEntry{
		time:    time.Now().Format("15:04:05.000"),
		message: formatRecvLog(conversationID, senderID, senderName, content, clientMsgID, serverMsgID, seq),
		kind:    debugLogKindRecv,
	})
}

func (m *DebugLogModel) AppendInfo(message string) {
	m.appendLog(debugLogEntry{
		time:    time.Now().Format("15:04:05.000"),
		message: message,
		kind:    debugLogKindInfo,
	})
}

func (m *DebugLogModel) AppendError(message string) {
	m.appendLog(debugLogEntry{
		time:    time.Now().Format("15:04:05.000"),
		message: message,
		kind:    debugLogKindError,
	})
}

func (m *DebugLogModel) AppendHistory(conversationID string, messageCount int, lastSenderID string, lastSeq int64) {
	m.appendLog(debugLogEntry{
		time:    time.Now().Format("15:04:05.000"),
		message: formatHistoryLog(conversationID, messageCount, lastSenderID, lastSeq),
		kind:    debugLogKindInfo,
	})
}

func (m *DebugLogModel) AppendSelfCheck(senderID, currentUserID string, self bool) {
	m.appendLog(debugLogEntry{
		time:    time.Now().Format("15:04:05.000"),
		message: formatSelfCheckLog(senderID, currentUserID, self),
		kind:    debugLogKindInfo,
	})
}

func (m *DebugLogModel) AppendConvTouch(conversationID, preview string) {
	m.appendLog(debugLogEntry{
		time:    time.Now().Format("15:04:05.000"),
		message: formatConvTouchLog(conversationID, preview),
		kind:    debugLogKindInfo,
	})
}

// ViewWithHeight 渲染调试面板，指定目标高度
func (m DebugLogModel) ViewWithHeight(theme Theme, targetHeight int) string {
	if !m.enabled {
		return ""
	}

	width := m.width
	height := m.height
	if width <= 0 {
		width = 55
	}
	if height <= 0 {
		height = 20
	}
	// 如果有目标高度，使用目标高度
	if targetHeight > 0 {
		height = targetHeight
	}

	logs := m.getLogs()
	header := theme.debugHeaderStyle().Render(" DEBUG ")
	lines := []string{header}

	// 计算可见行数
	visibleLines := height - 2 // 减去 header 和底部填充
	if visibleLines <= 0 {
		visibleLines = 10
	}

	// 从最新的日志开始显示
	start := 0
	if len(logs) > visibleLines {
		start = len(logs) - visibleLines
	}

	for i := start; i < len(logs); i++ {
		entry := logs[i]
		style := theme.debugInfoStyle()
		switch entry.kind {
		case debugLogKindSend:
			style = theme.debugSendStyle()
		case debugLogKindRecv:
			style = theme.debugRecvStyle()
		case debugLogKindError:
			style = theme.debugErrorStyle()
		}
		// 截断过长的消息
		msg := entry.message
		maxLen := width - 2 // 减去时间戳和前缀的宽度
		if maxLen < 10 {
			maxLen = 10
		}
		if len(msg) > maxLen {
			msg = msg[:maxLen-3] + "..."
		}
		lines = append(lines, style.Render(entry.time+" "+msg))
	}

	// 填充到底部
	for len(lines) < height {
		lines = append(lines, theme.debugInfoStyle().Render(""))
	}

	//log.Println("debug width", m.width, "height", height)
	return theme.panelStyle().Width(width).Height(height).Render(strings.Join(lines, "\n"))
}

func formatSendLog(conversationID, userID, requestID, content string) string {
	return "[SEND] " + truncate(content, 30)
}

func formatRecvLog(conversationID, senderID, senderName, content, clientMsgID, serverMsgID string, seq int64) string {
	return "[RECV] " + senderID + ": " + truncate(content, 25) + "; seq: " + strconv.FormatInt(seq, 10)
}

func formatHistoryLog(conversationID string, messageCount int, lastSenderID string, lastSeq int64) string {
	return "[HIST] " + conversationID + " (" + itoa(messageCount) + " msgs)"  + "lastSeq: " + strconv.FormatInt(lastSeq, 10)
}

func formatSelfCheckLog(senderID, currentUserID string, self bool) string {
	if self {
		return "[SELF] self=true (sent by me)"
	}
	return "[SELF] self=false"
}

func formatConvTouchLog(conversationID, preview string) string {
	return "[CONV] " + truncate(preview, 25)
}

func truncate(s string, maxLen int) string {
	if len(s) <= maxLen {
		return s
	}
	return s[:maxLen] + "..."
}

func itoa(n int) string {
	if n == 0 {
		return "0"
	}
	result := ""
	for n > 0 {
		result = string(rune('0'+n%10)) + result
		n /= 10
	}
	return result
}
