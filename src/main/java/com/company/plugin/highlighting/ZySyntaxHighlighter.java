package com.company.plugin.highlighting;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * ZY 语法高亮器
 * 基于 TextMate 语法文件 origami.tmLanguage.json 实现
 */
public class ZySyntaxHighlighter extends SyntaxHighlighterBase {
    
    @Override
    @NotNull
    public Lexer getHighlightingLexer() {
        return new ZyLexer();
    }
    
    @Override
    @NotNull
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        if (tokenType == ZyTokenTypes.KEYWORD) {
            return new TextAttributesKey[]{ZySyntaxHighlighterFactory.KEYWORD};
        } else if (tokenType == ZyTokenTypes.STRING) {
            return new TextAttributesKey[]{ZySyntaxHighlighterFactory.STRING};
        } else if (tokenType == ZyTokenTypes.NUMBER) {
            return new TextAttributesKey[]{ZySyntaxHighlighterFactory.NUMBER};
        } else if (tokenType == ZyTokenTypes.COMMENT) {
            return new TextAttributesKey[]{ZySyntaxHighlighterFactory.COMMENT};
        } else if (tokenType == ZyTokenTypes.IDENTIFIER) {
            return new TextAttributesKey[]{ZySyntaxHighlighterFactory.IDENTIFIER};
        } else if (tokenType == ZyTokenTypes.OPERATOR) {
            return new TextAttributesKey[]{ZySyntaxHighlighterFactory.OPERATOR};
        } else if (tokenType == ZyTokenTypes.PHP_KEYWORD) {
            return new TextAttributesKey[]{ZySyntaxHighlighterFactory.PHP_KEYWORD};
        } else if (tokenType == ZyTokenTypes.PHP_FUNCTION) {
            return new TextAttributesKey[]{ZySyntaxHighlighterFactory.PHP_FUNCTION};
        } else if (tokenType == ZyTokenTypes.PHP_VARIABLE) {
            return new TextAttributesKey[]{ZySyntaxHighlighterFactory.PHP_VARIABLE};
        } else if (tokenType == ZyTokenTypes.PHP_CONSTANT) {
            return new TextAttributesKey[]{ZySyntaxHighlighterFactory.PHP_CONSTANT};
        } else {
            return new TextAttributesKey[0];
        }
    }
}
