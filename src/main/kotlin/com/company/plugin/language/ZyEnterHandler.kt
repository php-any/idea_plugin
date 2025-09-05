package com.company.plugin.language

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.openapi.util.TextRange
import com.intellij.application.options.CodeStyle
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.Key
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.diagnostic.Logger

/**
 * ZY 回车换行缩进处理器
 * 在 .zy 文件中按下回车后，继承上一行的缩进（空格/制表符原样保留），保持作用域内缩进一致
 */
class ZyEnterHandler : EnterHandlerDelegateAdapter() {

    companion object {
        // 标记本次回车已由 preprocessEnter 处理，避免 postProcessEnter 再次插入缩进导致光标错位
        private val ENTER_HANDLED_KEY: Key<Boolean> = Key.create("zy.enter.handled")
        private val LOG: Logger = Logger.getInstance(ZyEnterHandler::class.java)
    }

    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffsetRef: Ref<Int>,
        caretAdvanceRef: Ref<Int>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?
    ): EnterHandlerDelegate.Result {
        if (!file.name.endsWith(".zy")) {
            return EnterHandlerDelegate.Result.Continue
        }

        val document = editor.document
        val offset = caretOffsetRef.get()
        if (offset <= 0) return EnterHandlerDelegate.Result.Continue

        val text = document.charsSequence
        // 向后寻找下一个非空白字符
        fun nextNonWs(from: Int): Char {
            var i = from
            while (i < text.length) {
                val ch = text[i]
                if (ch != ' ' && ch != '\t' && ch != '\r' && ch != '\n') return ch
                i++
            }
            return '\u0000'
        }
        // 向前寻找上一个非空白字符
        fun prevNonWs(before: Int): Char {
            var i = before - 1
            while (i >= 0) {
                val ch = text[i]
                if (ch != ' ' && ch != '\t' && ch != '\r' && ch != '\n') return ch
                i--
            }
            return '\u0000'
        }
        val prevChar = prevNonWs(offset)
        val nextChar = nextNonWs(offset)

        // 情况1：位于成对括号之间的回车，插入两行并正确缩进（{}、[]、()）
        if ((prevChar == '{' && nextChar == '}') || (prevChar == '[' && nextChar == ']') || (prevChar == '(' && nextChar == ')')) {
            val line = document.getLineNumber(offset)
            val lineStart = document.getLineStartOffset(line)
            val baseIndentText = document.getText(TextRange(lineStart, offset - 1))
            val leadingIndent = buildString {
                for (ch in baseIndentText) {
                    if (ch == ' ' || ch == '\t') append(ch) else break
                }
            }
            val indentOptions = CodeStyle.getSettings(file).getIndentOptions(file.fileType)
            val unitIndent = if (indentOptions.USE_TAB_CHARACTER) "\t" else " ".repeat(indentOptions.INDENT_SIZE)
            val innerIndent = leadingIndent + unitIndent

            val insertion = "\n" + innerIndent + "\n" + leadingIndent
            document.insertString(offset, insertion)
            // 直接设置光标位置到中间行缩进末尾，避免依赖 caretAdvanceRef 在 Stop 模式下可能无效
            editor.caretModel.moveToOffset(offset + 1 + innerIndent.length)
            editor.putUserData(ENTER_HANDLED_KEY, true)
            return EnterHandlerDelegate.Result.Stop
        }

        // 情况2：紧随开括号后的回车，当前行直接 +1 级缩进（适用于 { [ ()）
        if (prevChar == '{' || prevChar == '[' || prevChar == '(') {
            val line = document.getLineNumber(offset)
            val lineStart = document.getLineStartOffset(line)
            val baseIndentText = document.getText(TextRange(lineStart, lineStart + (document.getLineEndOffset(line) - lineStart).coerceAtLeast(0))).let { lineText ->
                // 仅提取行首空白作为基底缩进
                buildString {
                    for (ch in lineText) {
                        if (ch == ' ' || ch == '\t') append(ch) else break
                    }
                }
            }
            val leadingIndent = baseIndentText
            val indentOptions = CodeStyle.getSettings(file).getIndentOptions(file.fileType)
            val unitIndent = if (indentOptions.USE_TAB_CHARACTER) "\t" else " ".repeat(indentOptions.INDENT_SIZE)
            val innerIndent = leadingIndent + unitIndent
            val insertion = "\n" + innerIndent
            document.insertString(offset, insertion)
            caretAdvanceRef.set(1 + innerIndent.length)
            editor.putUserData(ENTER_HANDLED_KEY, true)
            return EnterHandlerDelegate.Result.Stop
        }

        return EnterHandlerDelegate.Result.Continue
    }

    override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): EnterHandlerDelegate.Result {
        if (!file.name.endsWith(".zy")) return EnterHandlerDelegate.Result.Continue

        // 如果 preprocessEnter 已处理本次回车，则跳过后续缩进调整，避免重复缩进导致光标位置错误
        if (editor.getUserData(ENTER_HANDLED_KEY) == true) {
            editor.putUserData(ENTER_HANDLED_KEY, false)
            return EnterHandlerDelegate.Result.Continue
        }

        val document: Document = editor.document
        val caretOffset = editor.caretModel.offset
        val currentLine = document.getLineNumber(caretOffset)
        if (currentLine <= 0) return EnterHandlerDelegate.Result.Continue

        val prevLine = currentLine - 1
        val prevStart = document.getLineStartOffset(prevLine)
        val prevEnd = document.getLineEndOffset(prevLine)
        val prevText = document.getText(TextRange(prevStart, prevEnd))

        // 计算上一行前导空白（保留空格/Tab 原样）
        val leadingIndent = buildString {
            for (ch in prevText) {
                if (ch == ' ' || ch == '\t') append(ch) else break
            }
        }

        // 根据上一行结尾和当前行起始，调整缩进层级
        val trimmedPrev = prevText.trimEnd()
        // 仅当上一行最后一个非空白字符是开括号时才增加一级
        fun lastNonWsChar(s: String): Char? {
            for (i in s.length - 1 downTo 0) {
                val ch = s[i]
                if (ch != ' ' && ch != '\t') return ch
            }
            return null
        }
        val lastCh = lastNonWsChar(trimmedPrev)
        val prevEndsWithOpen = lastCh == '{' || lastCh == '[' || lastCh == '('

        // 若当前行以闭合符开头，则减少一级缩进
        val currLineStart = document.getLineStartOffset(currentLine)
        val currLineEnd = document.getLineEndOffset(currentLine)
        val currHeadText = if (currLineStart < currLineEnd) document.getText(TextRange(currLineStart, (currLineStart + 3).coerceAtMost(currLineEnd))) else ""
        val currStartsWithClose = currHeadText.startsWith("}") || currHeadText.startsWith(")") || currHeadText.startsWith("]")

        // 依据项目代码风格设置确定缩进单位（Tab 或 N 个空格）
        val indentOptions = CodeStyle.getSettings(file).getIndentOptions(file.fileType)
        val unitIndent = if (indentOptions.USE_TAB_CHARACTER) "\t" else " ".repeat(indentOptions.INDENT_SIZE)
        val baseIndent = leadingIndent
        val finalIndent = buildString {
            append(baseIndent)
            if (prevEndsWithOpen) append(unitIndent)
            if (currStartsWithClose && length >= unitIndent.length) delete(length - unitIndent.length, length)
        }

        if (finalIndent.isNotEmpty()) {
            document.insertString(caretOffset, finalIndent)
            editor.caretModel.moveToOffset(caretOffset + finalIndent.length)
            return EnterHandlerDelegate.Result.DefaultSkipIndent
        }

        return EnterHandlerDelegate.Result.Continue
    }
}


