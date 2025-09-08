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
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.diagnostic.Logger

/**
 * ZY 回车换行缩进处理器
 * 重构版本：简化逻辑，直接响应，准确处理缩进
 */
class ZyEnterHandler : EnterHandlerDelegateAdapter() {

    companion object {
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
        // 只处理.zy文件
        if (!file.name.endsWith(".zy")) {
            return EnterHandlerDelegate.Result.Continue
        }

        val document = editor.document
        val offset = caretOffsetRef.get()
        
        // 获取当前行信息
        val currentLine = document.getLineNumber(offset)
        val currentLineStart = document.getLineStartOffset(currentLine)
        val currentLineEnd = document.getLineEndOffset(currentLine)
        val currentLineText = document.getText(TextRange(currentLineStart, currentLineEnd))
        
        // 获取光标前后的字符
        val beforeCursor = if (offset > currentLineStart) document.getText(TextRange(offset - 1, offset)) else ""
        val afterCursor = if (offset < currentLineEnd) document.getText(TextRange(offset, offset + 1)) else ""
        
        // 计算当前行的基础缩进
        val baseIndent = getLineIndent(currentLineText)
        
        // 获取缩进设置
        val indentOptions = CodeStyle.getSettings(file).getIndentOptions(file.fileType)
        val indentUnit = if (indentOptions.USE_TAB_CHARACTER) "\t" else " ".repeat(indentOptions.INDENT_SIZE)
        
        var newIndent = baseIndent
        var extraNewlines = ""
        
        // 处理特殊情况
        when {
            // 情况1：在成对括号之间（如 {|}、[|]、(|)）
            isPairBrackets(beforeCursor, afterCursor) -> {
                newIndent = baseIndent + indentUnit
                extraNewlines = "\n" + baseIndent
            }
            // 情况2：在开括号后面
            isOpenBracket(beforeCursor) -> {
                newIndent = baseIndent + indentUnit
            }
            // 情况3：在闭括号前面
            isCloseBracket(afterCursor) -> {
                newIndent = if (baseIndent.length >= indentUnit.length) {
                    baseIndent.substring(0, baseIndent.length - indentUnit.length)
                } else {
                    ""
                }
            }
            // 情况4：普通换行，继承当前行缩进
            else -> {
                // 检查当前行最后的非空白字符
                val trimmedLine = currentLineText.trimEnd()
                if (trimmedLine.endsWith("{") || trimmedLine.endsWith("[") || trimmedLine.endsWith("(")) {
                    newIndent = baseIndent + indentUnit
                }
            }
        }
        
        // 插入换行和缩进
        val insertion = "\n" + newIndent + extraNewlines
        document.insertString(offset, insertion)
        
        // 设置光标位置
        val newCaretPosition = offset + 1 + newIndent.length
        editor.caretModel.moveToOffset(newCaretPosition)
        
        LOG.debug("Enter processed: baseIndent='$baseIndent', newIndent='$newIndent', caretPos=$newCaretPosition")
        
        return EnterHandlerDelegate.Result.Stop
    }
    
    /**
     * 获取行的前导缩进
     */
    private fun getLineIndent(lineText: String): String {
        val indent = StringBuilder()
        for (char in lineText) {
            if (char == ' ' || char == '\t') {
                indent.append(char)
            } else {
                break
            }
        }
        return indent.toString()
    }
    
    /**
     * 检查是否在成对括号之间
     */
    private fun isPairBrackets(before: String, after: String): Boolean {
        return (before == "{" && after == "}") ||
               (before == "[" && after == "]") ||
               (before == "(" && after == ")")
    }
    
    /**
     * 检查是否是开括号
     */
    private fun isOpenBracket(char: String): Boolean {
        return char == "{" || char == "[" || char == "("
    }
    
    /**
     * 检查是否是闭括号
     */
    private fun isCloseBracket(char: String): Boolean {
        return char == "}" || char == "]" || char == ")"
    }
}