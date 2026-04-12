package ui

func helpView(locale LocaleName, theme Theme) string {
	return theme.titleStyle().Render(T(locale, keyHelpTitle)) + "\n\n" + theme.hintStyle().Render(T(locale, keyHelpBody))
}
