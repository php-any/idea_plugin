package com.company.plugin.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
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
        // 通用语法高亮属性键
        // 映射到默认颜色以确保即使未自定义配色也有可见高亮
        val KEYWORD = TextAttributesKey.createTextAttributesKey(
            "ZY_KEYWORD",
            DefaultLanguageHighlighterColors.KEYWORD
        )
        val STRING = TextAttributesKey.createTextAttributesKey(
            "ZY_STRING",
            DefaultLanguageHighlighterColors.STRING
        )
        val NUMBER = TextAttributesKey.createTextAttributesKey(
            "ZY_NUMBER",
            DefaultLanguageHighlighterColors.NUMBER
        )
        val COMMENT = TextAttributesKey.createTextAttributesKey(
            "ZY_COMMENT",
            DefaultLanguageHighlighterColors.LINE_COMMENT
        )
        val IDENTIFIER = TextAttributesKey.createTextAttributesKey(
            "ZY_IDENTIFIER",
            DefaultLanguageHighlighterColors.IDENTIFIER
        )
        val OPERATOR = TextAttributesKey.createTextAttributesKey(
            "ZY_OPERATOR",
            DefaultLanguageHighlighterColors.OPERATION_SIGN
        )
        
        // PHP 特定语法高亮属性键
        val PHP_KEYWORD = TextAttributesKey.createTextAttributesKey(
            "ZY_PHP_KEYWORD",
            DefaultLanguageHighlighterColors.KEYWORD
        )
        val PHP_FUNCTION = TextAttributesKey.createTextAttributesKey(
            "ZY_PHP_FUNCTION",
            DefaultLanguageHighlighterColors.FUNCTION_DECLARATION
        )
        val PHP_VARIABLE = TextAttributesKey.createTextAttributesKey(
            "ZY_PHP_VARIABLE",
            DefaultLanguageHighlighterColors.CONSTANT
        )
        val PHP_CONSTANT = TextAttributesKey.createTextAttributesKey(
            "ZY_PHP_CONSTANT",
            DefaultLanguageHighlighterColors.CONSTANT
        )
    }
}
