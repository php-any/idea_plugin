package com.company.plugin.language

import com.intellij.lexer.LexerBase
import com.intellij.openapi.util.TextRange
import com.intellij.psi.tree.IElementType
import com.intellij.psi.TokenType

/**
 * ZY 词法分析器（极简版）
 * 将文本拆分为 IDENTIFIER / WHITE_SPACE / OTHER 三类，避免整文件成为单一 PSI 元素
 */
class ZyLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var startOffset: Int = 0
    private var endOffset: Int = 0
    private var tokenStart: Int = 0
    private var tokenEnd: Int = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        this.tokenType = null
        advance()
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = endOffset

    override fun advance() {
        if (tokenEnd >= endOffset) {
            tokenType = null
            return
        }

        tokenStart = if (tokenEnd == 0) startOffset else tokenEnd
        var i = tokenStart
        if (i >= endOffset) {
            tokenType = null
            return
        }

        val ch = buffer[i]
        if (ch.isWhitespace()) {
            while (i < endOffset && buffer[i].isWhitespace()) i++
            tokenEnd = i
            tokenType = ZyTokenTypes.WHITE_SPACE
            return
        }

        if (isWordChar(ch)) {
            while (i < endOffset && isWordChar(buffer[i])) i++
            tokenEnd = i
            tokenType = ZyTokenTypes.IDENTIFIER
            return
        }

        // 其他字符
        tokenEnd = i + 1
        tokenType = ZyTokenTypes.OTHER
    }

    private fun Char.isWhitespace(): Boolean = this == ' ' || this == '\t' || this == '\n' || this == '\r'
    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
}


