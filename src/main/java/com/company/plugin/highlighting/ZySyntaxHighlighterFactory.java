package com.company.plugin.highlighting;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ZY 语法高亮工厂
 * 基于 TextMate 语法文件 origami.tmLanguage.json
 */
public class ZySyntaxHighlighterFactory extends SyntaxHighlighterFactory {
    
    // 通用语法高亮属性键
    // 映射到默认颜色以确保即使未自定义配色也有可见高亮
    public static final TextAttributesKey KEYWORD = TextAttributesKey.createTextAttributesKey(
            "ZY_KEYWORD",
            DefaultLanguageHighlighterColors.KEYWORD
    );
    public static final TextAttributesKey STRING = TextAttributesKey.createTextAttributesKey(
            "ZY_STRING",
            DefaultLanguageHighlighterColors.STRING
    );
    public static final TextAttributesKey NUMBER = TextAttributesKey.createTextAttributesKey(
            "ZY_NUMBER",
            DefaultLanguageHighlighterColors.NUMBER
    );
    public static final TextAttributesKey COMMENT = TextAttributesKey.createTextAttributesKey(
            "ZY_COMMENT",
            DefaultLanguageHighlighterColors.LINE_COMMENT
    );
    public static final TextAttributesKey IDENTIFIER = TextAttributesKey.createTextAttributesKey(
            "ZY_IDENTIFIER",
            DefaultLanguageHighlighterColors.IDENTIFIER
    );
    public static final TextAttributesKey OPERATOR = TextAttributesKey.createTextAttributesKey(
            "ZY_OPERATOR",
            DefaultLanguageHighlighterColors.OPERATION_SIGN
    );
    
    // PHP 特定语法高亮属性键
    public static final TextAttributesKey PHP_KEYWORD = TextAttributesKey.createTextAttributesKey(
            "ZY_PHP_KEYWORD",
            DefaultLanguageHighlighterColors.KEYWORD
    );
    public static final TextAttributesKey PHP_FUNCTION = TextAttributesKey.createTextAttributesKey(
            "ZY_PHP_FUNCTION",
            DefaultLanguageHighlighterColors.FUNCTION_DECLARATION
    );
    public static final TextAttributesKey PHP_VARIABLE = TextAttributesKey.createTextAttributesKey(
            "ZY_PHP_VARIABLE",
            DefaultLanguageHighlighterColors.CONSTANT
    );
    public static final TextAttributesKey PHP_CONSTANT = TextAttributesKey.createTextAttributesKey(
            "ZY_PHP_CONSTANT",
            DefaultLanguageHighlighterColors.CONSTANT
    );
    
    @Override
    @NotNull
    public SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project, @Nullable VirtualFile virtualFile) {
        return new ZySyntaxHighlighter();
    }
}
