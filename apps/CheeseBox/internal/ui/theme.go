package ui

import "github.com/charmbracelet/lipgloss"

type ThemeName string

const (
	ThemeClassic ThemeName = "classic"
	ThemeMatrix  ThemeName = "matrix"
	ThemePaper   ThemeName = "paper"
)

type Theme struct {
	Name          ThemeName
	titleColor    lipgloss.Color
	panelColor    lipgloss.Color
	panelBg       lipgloss.Color
	statusColor   lipgloss.Color
	statusBg      lipgloss.Color
	focusFg       lipgloss.Color
	focusBg       lipgloss.Color
	hintColor     lipgloss.Color
	metaColor     lipgloss.Color
	selfColor     lipgloss.Color
	otherColor    lipgloss.Color
	emptyColor    lipgloss.Color
	sectionColor  lipgloss.Color
	unreadFg      lipgloss.Color
	unreadBg      lipgloss.Color
	tabActiveFg   lipgloss.Color
	tabActiveBg   lipgloss.Color
	tabInactiveFg lipgloss.Color
	tabInactiveBg lipgloss.Color
	tabDivider    lipgloss.Color
}

func defaultTheme() Theme {
	return themeByName(ThemeClassic)
}

func themeByName(name ThemeName) Theme {
	switch name {
	case ThemeMatrix:
		return Theme{
			Name:          ThemeMatrix,
			titleColor:    lipgloss.Color("119"),
			panelColor:    lipgloss.Color("34"),
			panelBg:       lipgloss.Color("22"),
			statusColor:   lipgloss.Color("118"),
			statusBg:      lipgloss.Color("22"),
			focusFg:       lipgloss.Color("16"),
			focusBg:       lipgloss.Color("118"),
			hintColor:     lipgloss.Color("71"),
			metaColor:     lipgloss.Color("65"),
			selfColor:     lipgloss.Color("119"),
			otherColor:    lipgloss.Color("84"),
			emptyColor:    lipgloss.Color("65"),
			sectionColor:  lipgloss.Color("78"),
			unreadFg:      lipgloss.Color("16"),
			unreadBg:      lipgloss.Color("118"),
			tabActiveFg:   lipgloss.Color("16"),
			tabActiveBg:   lipgloss.Color("118"),
			tabInactiveFg: lipgloss.Color("71"),
			tabInactiveBg: lipgloss.Color("22"),
			tabDivider:    lipgloss.Color("35"),
		}
	case ThemePaper:
		return Theme{
			Name:          ThemePaper,
			titleColor:    lipgloss.Color("24"),
			panelColor:    lipgloss.Color("250"),
			panelBg:       lipgloss.Color("255"),
			statusColor:   lipgloss.Color("30"),
			statusBg:      lipgloss.Color("254"),
			focusFg:       lipgloss.Color("255"),
			focusBg:       lipgloss.Color("31"),
			hintColor:     lipgloss.Color("245"),
			metaColor:     lipgloss.Color("102"),
			selfColor:     lipgloss.Color("25"),
			otherColor:    lipgloss.Color("238"),
			emptyColor:    lipgloss.Color("246"),
			sectionColor:  lipgloss.Color("60"),
			unreadFg:      lipgloss.Color("255"),
			unreadBg:      lipgloss.Color("31"),
			tabActiveFg:   lipgloss.Color("255"),
			tabActiveBg:   lipgloss.Color("31"),
			tabInactiveFg: lipgloss.Color("240"),
			tabInactiveBg: lipgloss.Color("254"),
			tabDivider:    lipgloss.Color("250"),
		}
	default:
		return Theme{
			Name:          ThemeClassic,
			titleColor:    lipgloss.Color("252"),
			panelColor:    lipgloss.Color("240"),
			panelBg:       lipgloss.Color("235"),
			statusColor:   lipgloss.Color("10"),
			statusBg:      lipgloss.Color("236"),
			focusFg:       lipgloss.Color("229"),
			focusBg:       lipgloss.Color("25"),
			hintColor:     lipgloss.Color("245"),
			metaColor:     lipgloss.Color("244"),
			selfColor:     lipgloss.Color("229"),
			otherColor:    lipgloss.Color("252"),
			emptyColor:    lipgloss.Color("244"),
			sectionColor:  lipgloss.Color("110"),
			unreadFg:      lipgloss.Color("16"),
			unreadBg:      lipgloss.Color("110"),
			tabActiveFg:   lipgloss.Color("16"),
			tabActiveBg:   lipgloss.Color("110"),
			tabInactiveFg: lipgloss.Color("250"),
			tabInactiveBg: lipgloss.Color("236"),
			tabDivider:    lipgloss.Color("240"),
		}
	}
}

func nextTheme(name ThemeName) ThemeName {
	switch name {
	case ThemeClassic:
		return ThemeMatrix
	case ThemeMatrix:
		return ThemePaper
	default:
		return ThemeClassic
	}
}

func (t Theme) titleStyle() lipgloss.Style {
	return lipgloss.NewStyle().Bold(true).Foreground(t.titleColor)
}

func (t Theme) panelStyle() lipgloss.Style {
	return lipgloss.NewStyle().
		Border(lipgloss.RoundedBorder()).
		BorderForeground(t.panelColor).
		Background(t.panelBg).
		Padding(0, 1)
}

func (t Theme) statusStyle() lipgloss.Style {
	return lipgloss.NewStyle().
		Foreground(t.statusColor).
		Background(t.statusBg).
		Bold(true).
		Padding(0, 1)
}

func (t Theme) focusStyle() lipgloss.Style {
	return lipgloss.NewStyle().Foreground(t.focusFg).Background(t.focusBg).Bold(true)
}

func (t Theme) hintStyle() lipgloss.Style {
	return lipgloss.NewStyle().Foreground(t.hintColor)
}

func (t Theme) chatMetaStyle() lipgloss.Style {
	return lipgloss.NewStyle().Foreground(t.metaColor)
}

func (t Theme) chatContentSelf() lipgloss.Style {
	return lipgloss.NewStyle().Foreground(t.selfColor)
}

func (t Theme) chatContentOther() lipgloss.Style {
	return lipgloss.NewStyle().Foreground(t.otherColor)
}

func (t Theme) chatEmptyStateStyle() lipgloss.Style {
	return lipgloss.NewStyle().Foreground(t.emptyColor)
}

func (t Theme) sectionTitleStyle() lipgloss.Style {
	return lipgloss.NewStyle().Foreground(t.sectionColor).Bold(true)
}

func (t Theme) unreadBadgeStyle() lipgloss.Style {
	return lipgloss.NewStyle().
		Foreground(t.unreadFg).
		Background(t.unreadBg).
		Bold(true).
		Padding(0, 1)
}

func (t Theme) tabActiveStyle(focused bool) lipgloss.Style {
	style := lipgloss.NewStyle().
		Foreground(t.tabActiveFg).
		Background(t.tabActiveBg).
		Bold(true).
		Padding(0, 1)
	if focused {
		style = style.Underline(true)
	}
	return style
}

func (t Theme) tabInactiveStyle(focused bool) lipgloss.Style {
	style := lipgloss.NewStyle().
		Foreground(t.tabInactiveFg).
		Background(t.tabInactiveBg).
		Padding(0, 1)
	if focused {
		style = style.Underline(true)
	}
	return style
}

func (t Theme) tabDividerStyle() lipgloss.Style {
	return lipgloss.NewStyle().Foreground(t.tabDivider)
}
