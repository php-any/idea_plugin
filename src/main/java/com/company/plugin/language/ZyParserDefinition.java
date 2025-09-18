package com.company.plugin.language;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.TokenType;
import com.intellij.lang.PsiBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * ZY 解析器定义（极简版）
 * 仅提供词法、空白/注释集合和文件 PSI 封装，满足 IDE 将文件拆分成多个 Token 的需求
 */
public class ZyParserDefinition implements ParserDefinition {
    
    @Override
    @NotNull
    public Lexer createLexer(Project project) {
        return new com.company.plugin.highlighting.ZyLexer();
    }

    @Override
    @NotNull
    public PsiParser createParser(Project project) {
        return new PsiParser() {
            @Override
            @NotNull
            public ASTNode parse(@NotNull com.intellij.psi.tree.IElementType root, @NotNull PsiBuilder builder) {
                PsiBuilder.Marker marker = builder.mark();
                
                // 极简解析：直接消费所有 token
                while (!builder.eof()) {
                    builder.advanceLexer();
                }
                
                marker.done(root);
                return builder.getTreeBuilt();
            }
        };
    }

    @Override
    @NotNull
    public IFileElementType getFileNodeType() {
        return ZyTokenTypes.FILE;
    }

    @Override
    @NotNull
    public TokenSet getWhitespaceTokens() {
        return TokenSet.create(TokenType.WHITE_SPACE);
    }

    @Override
    @NotNull
    public TokenSet getCommentTokens() {
        return TokenSet.EMPTY;
    }

    @Override
    @NotNull
    public TokenSet getStringLiteralElements() {
        return TokenSet.EMPTY;
    }

    @Override
    @NotNull
    public PsiElement createElement(ASTNode node) {
        return node.getPsi();
    }

    @Override
    @NotNull
    public PsiFile createFile(FileViewProvider viewProvider) {
        return new ZyPsiFile(viewProvider);
    }
}
