package com.company.plugin.index;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于作用域的 ZY 语言解析器
 * 使用词法分析和语法分析，而不是简单的正则匹配
 */
public class ZyScopeBasedParser {
    
    /**
     * 词法单元类型
     */
    public enum TokenType {
        KEYWORD,        // 关键字 (namespace, class, function, public, private, etc.)
        IDENTIFIER,     // 标识符
        VARIABLE,       // 变量 ($name)
        BRACE_OPEN,     // {
        BRACE_CLOSE,    // }
        SEMICOLON,      // ;
        COLON,          // :
        COMMA,          // ,
        STRING_LITERAL, // 字符串字面量
        WHITESPACE,     // 空白字符
        NEWLINE,        // 换行符
        UNKNOWN         // 未知
    }
    
    /**
     * 词法单元
     */
    public static class Token {
        public final TokenType type;
        public final String value;
        public final int start;
        public final int end;
        
        public Token(TokenType type, String value, int start, int end) {
            this.type = type;
            this.value = value;
            this.start = start;
            this.end = end;
        }
        
        @Override
        public String toString() {
            return type + "(" + value + ")";
        }
    }
    
    /**
     * 作用域类型
     */
    public enum ScopeType {
        GLOBAL,         // 全局作用域
        NAMESPACE,      // 命名空间作用域
        CLASS,          // 类作用域
        METHOD,         // 方法作用域
        FUNCTION        // 函数作用域
    }
    
    /**
     * 作用域信息
     */
    public static class Scope {
        public final ScopeType type;
        public final String name;
        public final int start;
        public int end;
        public final Scope parent;
        public final List<Scope> children;
        
        public Scope(ScopeType type, String name, int start, int end, Scope parent) {
            this.type = type;
            this.name = name;
            this.start = start;
            this.end = end;
            this.parent = parent;
            this.children = new ArrayList<>();
            if (parent != null) {
                parent.children.add(this);
            }
        }
        
        /**
         * 检查位置是否在此作用域内
         */
        public boolean contains(int position) {
            return position >= start && position <= end;
        }
        
        /**
         * 获取最内层的作用域
         */
        public Scope getInnermostScope(int position) {
            for (Scope child : children) {
                if (child.contains(position)) {
                    return child.getInnermostScope(position);
                }
            }
            return this;
        }
        
        /**
         * 获取指定类型的作用域
         */
        public Scope getScopeOfType(ScopeType type) {
            if (this.type == type) {
                return this;
            }
            if (parent != null) {
                return parent.getScopeOfType(type);
            }
            return null;
        }
    }
    
    /**
     * 符号信息
     */
    public static class Symbol {
        public final String name;
        public final String kind;
        public final int offset;
        public final String namespace;
        public final String fqn;
        public final Scope scope;
        
        public Symbol(String name, String kind, int offset, String namespace, String fqn, Scope scope) {
            this.name = name;
            this.kind = kind;
            this.offset = offset;
            this.namespace = namespace;
            this.fqn = fqn;
            this.scope = scope;
        }
    }
    
    // 关键字集合
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
        "namespace", "class", "function", "public", "private", "protected", 
        "static", "final", "abstract", "string", "int", "bool", "array"
    ));
    
    // 词法分析正则表达式
    private static final Pattern TOKEN_PATTERN;
    
    static {
        // 使用更安全的方式构建正则表达式，避免在某些环境下出现语法错误
        StringBuilder patternBuilder = new StringBuilder();
        patternBuilder.append("(");
        patternBuilder.append("\\b(?:namespace|class|function|public|private|protected|static|final|abstract|string|int|bool|array)\\b");
        patternBuilder.append(")|(");
        patternBuilder.append("\\$[a-zA-Z_][a-zA-Z0-9_]*");
        patternBuilder.append(")|(");
        patternBuilder.append("[a-zA-Z_][a-zA-Z0-9_]*");
        patternBuilder.append(")|(");
        patternBuilder.append("\\{");
        patternBuilder.append(")|(");
        patternBuilder.append("\\}");
        patternBuilder.append(")|(");
        patternBuilder.append(";");
        patternBuilder.append(")|(");
        patternBuilder.append(":");
        patternBuilder.append(")|(");
        patternBuilder.append(",");
        patternBuilder.append(")|(");
        patternBuilder.append("\"(?:[^\"\\\\]|\\\\.)*\"");
        patternBuilder.append(")|(");
        patternBuilder.append("\\s+");
        patternBuilder.append(")|(");
        patternBuilder.append("\\n");
        patternBuilder.append(")|(");
        patternBuilder.append(".");
        patternBuilder.append(")");
        
        TOKEN_PATTERN = Pattern.compile(patternBuilder.toString());
    }
    
    /**
     * 词法分析
     */
    public static List<Token> tokenize(String text) {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        
        while (matcher.find()) {
            TokenType type = TokenType.UNKNOWN;
            String value = matcher.group();
            
            if (matcher.group("keyword") != null) {
                type = TokenType.KEYWORD;
            } else if (matcher.group("variable") != null) {
                type = TokenType.VARIABLE;
            } else if (matcher.group("identifier") != null) {
                type = KEYWORDS.contains(value) ? TokenType.KEYWORD : TokenType.IDENTIFIER;
            } else if (matcher.group("brace_open") != null) {
                type = TokenType.BRACE_OPEN;
            } else if (matcher.group("brace_close") != null) {
                type = TokenType.BRACE_CLOSE;
            } else if (matcher.group("semicolon") != null) {
                type = TokenType.SEMICOLON;
            } else if (matcher.group("colon") != null) {
                type = TokenType.COLON;
            } else if (matcher.group("comma") != null) {
                type = TokenType.COMMA;
            } else if (matcher.group("string") != null) {
                type = TokenType.STRING_LITERAL;
            } else if (matcher.group("whitespace") != null) {
                type = TokenType.WHITESPACE;
            } else if (matcher.group("newline") != null) {
                type = TokenType.NEWLINE;
            }
            
            tokens.add(new Token(type, value, matcher.start(), matcher.end()));
        }
        
        return tokens;
    }
    
    /**
     * 语法分析 - 构建作用域树
     */
    public static Scope parseScopes(List<Token> tokens) {
        Scope root = new Scope(ScopeType.GLOBAL, "", 0, Integer.MAX_VALUE, null);
        Scope current = root;
        Stack<Scope> scopeStack = new Stack<>();
        scopeStack.push(root);
        
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            
            switch (token.type) {
                case KEYWORD:
                    if ("namespace".equals(token.value)) {
                        // 解析命名空间
                        String namespaceName = parseNamespaceName(tokens, i);
                        if (namespaceName != null) {
                            Scope namespaceScope = new Scope(ScopeType.NAMESPACE, namespaceName, token.start, -1, current);
                            scopeStack.push(namespaceScope);
                            current = namespaceScope;
                        }
                    } else if ("class".equals(token.value)) {
                        // 解析类
                        String className = parseClassName(tokens, i);
                        if (className != null) {
                            Scope classScope = new Scope(ScopeType.CLASS, className, token.start, -1, current);
                            scopeStack.push(classScope);
                            current = classScope;
                        }
                    } else if ("function".equals(token.value)) {
                        // 解析函数/方法
                        String functionName = parseFunctionName(tokens, i);
                        if (functionName != null) {
                            ScopeType funcType = current.type == ScopeType.CLASS ? ScopeType.METHOD : ScopeType.FUNCTION;
                            Scope functionScope = new Scope(funcType, functionName, token.start, -1, current);
                            scopeStack.push(functionScope);
                            current = functionScope;
                        }
                    }
                    break;
                    
                case BRACE_OPEN:
                    // 开始新的作用域
                    break;
                    
                case BRACE_CLOSE:
                    // 结束当前作用域
                    if (!scopeStack.isEmpty()) {
                        Scope closingScope = scopeStack.pop();
                        closingScope.end = token.end;
                        if (!scopeStack.isEmpty()) {
                            current = scopeStack.peek();
                        }
                    }
                    break;
            }
        }
        
        return root;
    }
    
    /**
     * 解析命名空间名称
     */
    private static String parseNamespaceName(List<Token> tokens, int startIndex) {
        for (int i = startIndex + 1; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.type == TokenType.IDENTIFIER) {
                return token.value;
            } else if (token.type == TokenType.SEMICOLON) {
                break;
            }
        }
        return null;
    }
    
    /**
     * 解析类名
     */
    private static String parseClassName(List<Token> tokens, int startIndex) {
        for (int i = startIndex + 1; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.type == TokenType.IDENTIFIER) {
                return token.value;
            } else if (token.type == TokenType.BRACE_OPEN) {
                break;
            }
        }
        return null;
    }
    
    /**
     * 解析函数名
     */
    private static String parseFunctionName(List<Token> tokens, int startIndex) {
        for (int i = startIndex + 1; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.type == TokenType.IDENTIFIER) {
                return token.value;
            } else if (token.type == TokenType.BRACE_OPEN) {
                break;
            }
        }
        return null;
    }
    
    /**
     * 提取符号信息
     */
    public static List<Symbol> extractSymbols(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            List<Token> tokens = tokenize(text);
            Scope root = parseScopes(tokens);
            List<Symbol> symbols = new ArrayList<>();
            
            // 获取命名空间
            String namespace = null;
            Scope namespaceScope = root.getScopeOfType(ScopeType.NAMESPACE);
            if (namespaceScope != null) {
                namespace = namespaceScope.name;
            }
            
            // 遍历所有作用域，提取符号
            extractSymbolsFromScope(root, namespace, symbols, text);
            
            return symbols;
        } catch (Exception e) {
            // 如果解析失败，返回空列表而不是抛出异常
            return new ArrayList<>();
        }
    }
    
    /**
     * 从作用域中提取符号
     */
    private static void extractSymbolsFromScope(Scope scope, String namespace, List<Symbol> symbols, String text) {
        // 处理当前作用域的符号
        if (scope.type == ScopeType.CLASS) {
            // 类符号
            String fqn = namespace != null ? namespace + "\\" + scope.name : scope.name;
            symbols.add(new Symbol(scope.name, "class", scope.start, namespace, fqn, scope));
            
            // 查找类属性（在类作用域内但不在方法内的变量）
            findClassProperties(scope, namespace, symbols, text);
            
        } else if (scope.type == ScopeType.METHOD) {
            // 方法符号
            Scope classScope = scope.getScopeOfType(ScopeType.CLASS);
            if (classScope != null) {
                String fqn = namespace != null ? namespace + "\\" + classScope.name + "::" + scope.name : classScope.name + "::" + scope.name;
                symbols.add(new Symbol(scope.name, "method", scope.start, namespace, fqn, scope));
            }
            
        } else if (scope.type == ScopeType.FUNCTION) {
            // 函数符号
            String fqn = namespace != null ? namespace + "\\" + scope.name : scope.name;
            symbols.add(new Symbol(scope.name, "function", scope.start, namespace, fqn, scope));
        }
        
        // 递归处理子作用域
        for (Scope child : scope.children) {
            extractSymbolsFromScope(child, namespace, symbols, text);
        }
    }
    
    /**
     * 查找类属性
     */
    private static void findClassProperties(Scope classScope, String namespace, List<Symbol> symbols, String text) {
        // 获取类作用域内的所有方法
        List<Scope> methods = new ArrayList<>();
        for (Scope child : classScope.children) {
            if (child.type == ScopeType.METHOD) {
                methods.add(child);
            }
        }
        
        // 对方法按开始位置排序
        methods.sort((a, b) -> Integer.compare(a.start, b.start));
        
        // 查找类属性：在类开始后、第一个方法前的变量声明
        int classStart = classScope.start;
        int propertyEnd = methods.isEmpty() ? classScope.end : methods.get(0).start;
        
        // 在属性区域内查找变量声明
        if (propertyEnd > classStart) {
            String propertyText = text.substring(classStart, propertyEnd);
            List<Token> propertyTokens = tokenize(propertyText);
            
            // 查找变量声明模式：type $name; 或 $name;
            for (int i = 0; i < propertyTokens.size(); i++) {
                Token token = propertyTokens.get(i);
                if (token.type == TokenType.VARIABLE) {
                    // 检查是否是属性声明（后面有分号）
                    boolean isProperty = false;
                    for (int j = i + 1; j < propertyTokens.size(); j++) {
                        Token nextToken = propertyTokens.get(j);
                        if (nextToken.type == TokenType.SEMICOLON) {
                            isProperty = true;
                            break;
                        } else if (nextToken.type == TokenType.BRACE_OPEN || 
                                   nextToken.type == TokenType.BRACE_CLOSE ||
                                   nextToken.type == TokenType.KEYWORD) {
                            break;
                        }
                    }
                    
                    if (isProperty) {
                        String propertyName = token.value; // 包含 $ 符号
                        String fqn = namespace != null ? namespace + "\\" + classScope.name + "::" + propertyName : classScope.name + "::" + propertyName;
                        symbols.add(new Symbol(propertyName, "property", classStart + token.start, namespace, fqn, classScope));
                    }
                }
            }
        }
    }
}