package com.company.plugin.highlighting;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.TokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.HashSet;

/**
 * ZY 词法分析器
 * 基于 TextMate 语法文件 origami.tmLanguage.json 实现
 */
public class ZyLexer extends LexerBase {
    
    private CharSequence buffer;
    private int bufferEnd;
    private int tokenStart;
    private int tokenEnd;
    private IElementType tokenType;
    
    // ZY 语言关键字集合
    private static final Set<String> ZY_KEYWORDS = new HashSet<String>() {{
        add("function");
        add("if");
        add("else");
        add("for");
        add("while");
        add("return");
        add("var");
        add("let");
        add("const");
        add("true");
        add("false");
        add("null");
        add("undefined");
        add("import");
        add("export");
        add("class");
        add("interface");
        add("struct");
    }};
    
    // PHP 关键字集合
    private static final Set<String> PHP_KEYWORDS = new HashSet<String>() {{
        // PHP 控制结构
        add("if");
        add("else");
        add("elseif");
        add("endif");
        add("while");
        add("endwhile");
        add("for");
        add("foreach");
        add("endforeach");
        add("do");
        add("switch");
        add("case");
        add("default");
        add("break");
        add("continue");
        add("goto");
        
        // PHP 函数和类
        add("function");
        add("class");
        add("interface");
        add("trait");
        add("namespace");
        add("use");
        add("as");
        add("public");
        add("private");
        add("protected");
        add("static");
        add("abstract");
        add("final");
        add("extends");
        add("implements");
        add("new");
        add("clone");
        add("instanceof");
        
        // PHP 变量和常量
        add("global");
        add("const");
        add("define");
        add("defined");
        
        // PHP 异常处理
        add("try");
        add("catch");
        add("finally");
        add("throw");
        
        // PHP 包含和引用
        add("include");
        add("include_once");
        add("require");
        add("require_once");
        
        // PHP 其他关键字
        add("return");
        add("yield");
        add("echo");
        add("print");
        add("print_r");
        add("var_dump");
        add("isset");
        add("empty");
        add("unset");
        add("die");
        add("exit");
        
        // PHP 类型声明
        add("array");
        add("string");
        add("int");
        add("float");
        add("bool");
        add("object");
        add("mixed");
        add("void");
        add("callable");
        add("iterable");
        add("resource");
        add("null");
        add("false");
        add("true");
    }};
    
    // PHP 内置函数集合
    private static final Set<String> PHP_FUNCTIONS = new HashSet<String>() {{
        // 字符串函数
        add("strlen");
        add("strpos");
        add("str_replace");
        add("substr");
        add("trim");
        add("ltrim");
        add("rtrim");
        add("strtolower");
        add("strtoupper");
        add("ucfirst");
        add("ucwords");
        add("str_split");
        add("explode");
        add("implode");
        add("sprintf");
        add("printf");
        add("htmlspecialchars");
        add("htmlentities");
        add("strip_tags");
        
        // 数组函数
        add("array");
        add("count");
        add("sizeof");
        add("array_push");
        add("array_pop");
        add("array_shift");
        add("array_unshift");
        add("array_merge");
        add("array_slice");
        add("array_splice");
        add("array_keys");
        add("array_values");
        add("array_search");
        add("in_array");
        add("array_unique");
        add("array_reverse");
        add("sort");
        add("rsort");
        add("asort");
        add("arsort");
        add("ksort");
        add("krsort");
        add("usort");
        add("uasort");
        add("uksort");
        
        // 文件系统函数
        add("file_exists");
        add("is_file");
        add("is_dir");
        add("file_get_contents");
        add("file_put_contents");
        add("fopen");
        add("fclose");
        add("fread");
        add("fwrite");
        add("fgets");
        add("fgetc");
        add("feof");
        add("fseek");
        add("ftell");
        add("copy");
        add("move_uploaded_file");
        add("unlink");
        add("mkdir");
        add("rmdir");
        add("chmod");
        add("chown");
        
        // 数学函数
        add("abs");
        add("ceil");
        add("floor");
        add("round");
        add("min");
        add("max");
        add("rand");
        add("mt_rand");
        add("pi");
        add("pow");
        add("sqrt");
        add("sin");
        add("cos");
        add("tan");
        add("asin");
        add("acos");
        add("atan");
        add("log");
        add("exp");
        
        // 日期时间函数
        add("date");
        add("time");
        add("strtotime");
        add("mktime");
        add("getdate");
        add("gmdate");
        add("microtime");
        
        // 其他常用函数
        add("json_encode");
        add("json_decode");
        add("serialize");
        add("unserialize");
        add("var_dump");
        add("print_r");
        add("gettype");
        add("is_array");
        add("is_string");
        add("is_numeric");
        add("is_null");
        add("is_bool");
        add("filter_var");
        add("filter_input");
        add("preg_match");
        add("preg_replace");
        add("preg_split");
    }};
    
    // PHP 常量集合
    private static final Set<String> PHP_CONSTANTS = new HashSet<String>() {{
        // PHP 预定义常量
        add("PHP_VERSION");
        add("PHP_OS");
        add("PHP_EOL");
        add("PHP_INT_MAX");
        add("PHP_INT_MIN");
        add("PHP_FLOAT_MAX");
        add("PHP_FLOAT_MIN");
        add("PHP_SAPI");
        add("PHP_BINARY");
        
        // 错误级别常量
        add("E_ERROR");
        add("E_WARNING");
        add("E_PARSE");
        add("E_NOTICE");
        add("E_CORE_ERROR");
        add("E_CORE_WARNING");
        add("E_COMPILE_ERROR");
        add("E_COMPILE_WARNING");
        add("E_USER_ERROR");
        add("E_USER_WARNING");
        add("E_USER_NOTICE");
        add("E_STRICT");
        add("E_RECOVERABLE_ERROR");
        add("E_DEPRECATED");
        add("E_USER_DEPRECATED");
        add("E_ALL");
        
        // 文件系统常量
        add("DIRECTORY_SEPARATOR");
        add("PATH_SEPARATOR");
        add("FILE_SEPARATOR");
        
        // 其他常量
        add("TRUE");
        add("FALSE");
        add("NULL");
        add("M_PI");
        add("M_E");
        add("M_LOG2E");
        add("M_LOG10E");
        add("M_LN2");
        add("M_LN10");
    }};
    
    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.bufferEnd = endOffset;
        this.tokenStart = startOffset;
        this.tokenEnd = startOffset;
        this.tokenType = null;
        if (startOffset < endOffset) {
            advance();
        }
    }
    
    @Override
    public int getState() {
        return 0;
    }
    
    @Override
    @Nullable
    public IElementType getTokenType() {
        return tokenType;
    }
    
    @Override
    public int getTokenStart() {
        return tokenStart;
    }
    
    @Override
    public int getTokenEnd() {
        return tokenEnd;
    }
    
    /**
     * 前进到下一个标记
     * 根据当前字符类型识别并创建相应的标记
     */
    @Override
    public void advance() {
        tokenStart = tokenEnd;
        if (tokenEnd >= bufferEnd) {
            tokenType = null;
            return;
        }

        char currentChar = buffer.charAt(tokenEnd);

        // 处理空白字符为独立 token，确保 token 序列连续且不出现空洞
        if (Character.isWhitespace(currentChar)) {
            while (tokenEnd < bufferEnd && Character.isWhitespace(buffer.charAt(tokenEnd))) {
                tokenEnd++;
            }
            tokenType = TokenType.WHITE_SPACE;
            return;
        }

        // 简化的token识别逻辑，减少复杂性
        if (currentChar == '/' && tokenEnd + 1 < bufferEnd) {
            if (buffer.charAt(tokenEnd + 1) == '/') {
                // 单行注释
                tokenEnd += 2;
                while (tokenEnd < bufferEnd && buffer.charAt(tokenEnd) != '\n') {
                    tokenEnd++;
                }
                tokenType = ZyTokenTypes.COMMENT;
            } else if (buffer.charAt(tokenEnd + 1) == '*') {
                // 多行注释
                tokenEnd += 2;
                while (tokenEnd < bufferEnd - 1) {
                    if (buffer.charAt(tokenEnd) == '*' && buffer.charAt(tokenEnd + 1) == '/') {
                        tokenEnd += 2;
                        break;
                    }
                    tokenEnd++;
                }
                tokenType = ZyTokenTypes.COMMENT;
            } else {
                tokenEnd++;
                tokenType = ZyTokenTypes.OPERATOR;
            }
        } else if (currentChar == '"' || currentChar == '\'') {
            // 字符串字面量
            char quote = currentChar;
            tokenEnd++;
            while (tokenEnd < bufferEnd && buffer.charAt(tokenEnd) != quote) {
                if (buffer.charAt(tokenEnd) == '\\' && tokenEnd + 1 < bufferEnd) {
                    tokenEnd += 2;
                } else {
                    tokenEnd++;
                }
            }
            if (tokenEnd < bufferEnd) tokenEnd++;
            tokenType = ZyTokenTypes.STRING;
        } else if (Character.isDigit(currentChar)) {
            // 数字字面量
            tokenEnd++;
            while (tokenEnd < bufferEnd && (Character.isDigit(buffer.charAt(tokenEnd)) || buffer.charAt(tokenEnd) == '.')) {
                tokenEnd++;
            }
            tokenType = ZyTokenTypes.NUMBER;
        } else if (currentChar == '$') {
            // PHP 变量 - 包含 $ 符号本身
            tokenEnd++;
            while (tokenEnd < bufferEnd && (Character.isLetterOrDigit(buffer.charAt(tokenEnd)) || buffer.charAt(tokenEnd) == '_')) {
                tokenEnd++;
            }
            tokenType = ZyTokenTypes.PHP_VARIABLE;
        } else if (Character.isLetter(currentChar) || currentChar == '_') {
            // 标识符或关键字
            tokenEnd++;
            while (tokenEnd < bufferEnd && (Character.isLetterOrDigit(buffer.charAt(tokenEnd)) || buffer.charAt(tokenEnd) == '_')) {
                tokenEnd++;
            }
            String text = buffer.subSequence(tokenStart, tokenEnd).toString();
            tokenType = getTokenTypeForText(text);
        } else {
            // 其他字符
            tokenEnd++;
            tokenType = ZyTokenTypes.OPERATOR;
        }
    }
    
    /**
     * 根据文本内容确定标记类型
     * @param text 要检查的文本
     * @return 对应的标记类型
     */
    private IElementType getTokenTypeForText(String text) {
        if (isZyKeyword(text)) {
            return ZyTokenTypes.KEYWORD;
        } else if (isPhpKeyword(text)) {
            return ZyTokenTypes.PHP_KEYWORD;
        } else if (isPhpFunction(text)) {
            return ZyTokenTypes.PHP_FUNCTION;
        } else if (isPhpConstant(text)) {
            return ZyTokenTypes.PHP_CONSTANT;
        } else {
            return ZyTokenTypes.IDENTIFIER;
        }
    }
    
    /**
     * 检查文本是否为 ZY 语言关键字
     * @param text 要检查的文本
     * @return 如果是 ZY 关键字返回 true，否则返回 false
     */
    private boolean isZyKeyword(String text) {
        return ZY_KEYWORDS.contains(text);
    }
    
    /**
     * 检查文本是否为 PHP 关键字
     * @param text 要检查的文本
     * @return 如果是 PHP 关键字返回 true，否则返回 false
     */
    private boolean isPhpKeyword(String text) {
        return PHP_KEYWORDS.contains(text);
    }
    
    /**
     * 检查文本是否为 PHP 内置函数
     * @param text 要检查的文本
     * @return 如果是 PHP 函数返回 true，否则返回 false
     */
    private boolean isPhpFunction(String text) {
        return PHP_FUNCTIONS.contains(text);
    }
    
    /**
     * 检查文本是否为 PHP 常量
     * @param text 要检查的文本
     * @return 如果是 PHP 常量返回 true，否则返回 false
     */
    private boolean isPhpConstant(String text) {
        return PHP_CONSTANTS.contains(text);
    }
    
    @Override
    @NotNull
    public CharSequence getBufferSequence() {
        return buffer;
    }
    
    @Override
    public int getBufferEnd() {
        return bufferEnd;
    }
}
