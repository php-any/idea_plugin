package com.company.plugin.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * ZY 语法高亮工厂
 * 基于 TextMate 语法文件 origami.tmLanguage.json
 */
class ZySyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter {
        return ZySyntaxHighlighter()
    }
    
    companion object {
        // 语法高亮属性键
        val KEYWORD = TextAttributesKey.createTextAttributesKey("ZY_KEYWORD")
        val STRING = TextAttributesKey.createTextAttributesKey("ZY_STRING")
        val NUMBER = TextAttributesKey.createTextAttributesKey("ZY_NUMBER")
        val COMMENT = TextAttributesKey.createTextAttributesKey("ZY_COMMENT")
        val IDENTIFIER = TextAttributesKey.createTextAttributesKey("ZY_IDENTIFIER")
        val OPERATOR = TextAttributesKey.createTextAttributesKey("ZY_OPERATOR")
    }
}
