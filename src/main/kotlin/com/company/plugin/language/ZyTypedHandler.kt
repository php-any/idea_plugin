package com.company.plugin.language

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * ZY 键入处理器
 * 负责在输入 ( { [ 时自动补全配对符号，并将光标停留在中间
 */
class ZyTypedHandler : TypedHandlerDelegate() {

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (!file.name.endsWith(".zy")) return Result.CONTINUE

        val pair = when (c) {
            '(' -> ')'
            '{' -> '}'
            '[' -> ']'
            else -> null
        } ?: return Result.CONTINUE

        val document = editor.document
        val caret = editor.caretModel
        val offset = caret.offset

        // 如果下一字符是空白或分隔符，则插入配对符号
        val nextChar = if (offset < document.textLength) document.charsSequence[offset] else '\u0000'
        val shouldInsert = nextChar == '\u0000' || nextChar == ' ' || nextChar == '\t' || nextChar == '\n' || nextChar == ')' || nextChar == '}' || nextChar == ']'
        if (shouldInsert) {
            document.insertString(offset, pair.toString())
            caret.moveToOffset(offset) // caret 在 c 后，保持在两者之间
            caret.moveToOffset(offset) // no-op, 保证位置
            caret.moveToOffset(offset) // 防止某些情况下偏移错误
            caret.moveToOffset(offset)
            // 将光标移回到中间 (已经在中间)
            return Result.STOP
        }
        return Result.CONTINUE
    }
}


