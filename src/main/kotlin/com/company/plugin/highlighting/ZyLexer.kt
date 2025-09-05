package com.company.plugin.highlighting

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType
import com.intellij.psi.TokenType

/**
 * ZY 词法分析器
 * 基于 TextMate 语法文件 origami.tmLanguage.json 实现
 */
class ZyLexer : LexerBase() {
    
    private var buffer: CharSequence? = null
    private var bufferEnd: Int = 0
    private var tokenStart: Int = 0
    private var tokenEnd: Int = 0
    private var tokenType: IElementType? = null
    
    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.bufferEnd = endOffset
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        this.tokenType = null
        if (startOffset < endOffset) {
            advance()
        }
    }
    
    override fun getState(): Int = 0
    
    override fun getTokenType(): IElementType? = tokenType
    
    override fun getTokenStart(): Int = tokenStart
    
    override fun getTokenEnd(): Int = tokenEnd
    
    /**
     * 前进到下一个标记
     * 根据当前字符类型识别并创建相应的标记
     */
    override fun advance() {
        tokenStart = tokenEnd
        if (tokenEnd >= bufferEnd) {
            tokenType = null
            return
        }

        val currentChar = buffer!![tokenEnd]

        // 处理空白字符为独立 token，确保 token 序列连续且不出现空洞
        if (currentChar.isWhitespace()) {
            while (tokenEnd < bufferEnd && buffer!![tokenEnd].isWhitespace()) {
                tokenEnd++
            }
            tokenType = TokenType.WHITE_SPACE
            return
        }

        // 简化的token识别逻辑，减少复杂性
        when {
            currentChar == '/' && tokenEnd + 1 < bufferEnd -> {
                if (buffer!![tokenEnd + 1] == '/') {
                    // 单行注释
                    tokenEnd += 2
                    while (tokenEnd < bufferEnd && buffer!![tokenEnd] != '\n') {
                        tokenEnd++
                    }
                    tokenType = ZyTokenTypes.COMMENT
                } else if (buffer!![tokenEnd + 1] == '*') {
                    // 多行注释
                    tokenEnd += 2
                    while (tokenEnd < bufferEnd - 1) {
                        if (buffer!![tokenEnd] == '*' && buffer!![tokenEnd + 1] == '/') {
                            tokenEnd += 2
                            break
                        }
                        tokenEnd++
                    }
                    tokenType = ZyTokenTypes.COMMENT
                } else {
                    tokenEnd++
                    tokenType = ZyTokenTypes.OPERATOR
                }
            }
            currentChar == '"' || currentChar == '\'' -> {
                // 字符串字面量
                val quote = currentChar
                tokenEnd++
                while (tokenEnd < bufferEnd && buffer!![tokenEnd] != quote) {
                    if (buffer!![tokenEnd] == '\\' && tokenEnd + 1 < bufferEnd) {
                        tokenEnd += 2
                    } else {
                        tokenEnd++
                    }
                }
                if (tokenEnd < bufferEnd) tokenEnd++
                tokenType = ZyTokenTypes.STRING
            }
            currentChar.isDigit() -> {
                // 数字字面量
                tokenEnd++
                while (tokenEnd < bufferEnd && (buffer!![tokenEnd].isDigit() || buffer!![tokenEnd] == '.')) {
                    tokenEnd++
                }
                tokenType = ZyTokenTypes.NUMBER
            }
            currentChar.isLetter() || currentChar == '_' -> {
                // 标识符或关键字
                tokenEnd++
                while (tokenEnd < bufferEnd && (buffer!![tokenEnd].isLetterOrDigit() || buffer!![tokenEnd] == '_')) {
                    tokenEnd++
                }
                val text = buffer!!.subSequence(tokenStart, tokenEnd).toString()
                tokenType = if (isKeyword(text)) ZyTokenTypes.KEYWORD else ZyTokenTypes.IDENTIFIER
            }
            else -> {
                // 其他字符
                tokenEnd++
                tokenType = ZyTokenTypes.OPERATOR
            }
        }
    }
    
    /**
     * 检查文本是否为关键字
     * @param text 要检查的文本
     * @return 如果是关键字返回 true，否则返回 false
     */
    private fun isKeyword(text: String): Boolean {
        // ZY 语言支持的关键字列表
        val keywords = setOf(
            "function", "if", "else", "for", "while", "return", "var", "let", "const",
            "true", "false", "null", "undefined", "import", "export", "class", "interface"
        )
        return keywords.contains(text)
    }
    
    override fun getBufferSequence(): CharSequence = buffer!!
    
    override fun getBufferEnd(): Int = bufferEnd
}
