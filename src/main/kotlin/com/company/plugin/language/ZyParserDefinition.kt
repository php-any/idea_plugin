package com.company.plugin.language

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.TokenType

/**
 * ZY 解析器定义（极简版）
 * 仅提供词法、空白/注释集合和文件 PSI 封装，满足 IDE 将文件拆分成多个 Token 的需求
 */
class ZyParserDefinition : ParserDefinition {
    override fun createLexer(project: Project): Lexer = ZyLexer()

    override fun createParser(project: Project): PsiParser = PsiParser { root, builder ->
        val marker = builder.mark()
        while (!builder.eof()) builder.advanceLexer()
        marker.done(root)
        builder.treeBuilt
    }

    override fun getFileNodeType(): IFileElementType = ZyTokenTypes.FILE

    override fun getWhitespaceTokens(): TokenSet = TokenSet.create(TokenType.WHITE_SPACE)

    override fun getCommentTokens(): TokenSet = TokenSet.EMPTY

    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun createElement(node: ASTNode): PsiElement = node.psi

    override fun createFile(viewProvider: FileViewProvider): PsiFile = ZyPsiFile(viewProvider)
}


