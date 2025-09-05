package com.company.plugin.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

/**
 * ZY 语法高亮器
 * 基于 TextMate 语法文件 origami.tmLanguage.json 实现
 */
class ZySyntaxHighlighter : SyntaxHighlighterBase() {
    
    override fun getHighlightingLexer(): Lexer {
        return ZyLexer()
    }
    
    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return when (tokenType) {
            // 通用标记类型
            ZyTokenTypes.KEYWORD -> arrayOf(ZySyntaxHighlighterFactory.KEYWORD)
            ZyTokenTypes.STRING -> arrayOf(ZySyntaxHighlighterFactory.STRING)
            ZyTokenTypes.NUMBER -> arrayOf(ZySyntaxHighlighterFactory.NUMBER)
            ZyTokenTypes.COMMENT -> arrayOf(ZySyntaxHighlighterFactory.COMMENT)
            ZyTokenTypes.IDENTIFIER -> arrayOf(ZySyntaxHighlighterFactory.IDENTIFIER)
            ZyTokenTypes.OPERATOR -> arrayOf(ZySyntaxHighlighterFactory.OPERATOR)
            
            // PHP 特定标记类型
            ZyTokenTypes.PHP_KEYWORD -> arrayOf(ZySyntaxHighlighterFactory.PHP_KEYWORD)
            ZyTokenTypes.PHP_FUNCTION -> arrayOf(ZySyntaxHighlighterFactory.PHP_FUNCTION)
            ZyTokenTypes.PHP_VARIABLE -> arrayOf(ZySyntaxHighlighterFactory.PHP_VARIABLE)
            ZyTokenTypes.PHP_CONSTANT -> arrayOf(ZySyntaxHighlighterFactory.PHP_CONSTANT)
            
            else -> emptyArray()
        }
    }
}
