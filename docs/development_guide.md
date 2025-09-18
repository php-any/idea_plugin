### ZY 语言插件开发/修改指南

#### 技术栈与约束

- **语言**: Java（禁止 Kotlin、外部 LSP）
- **平台**: IntelliJ Platform SDK（优先使用本地 IDEA 路径）
- **注释**: 所有类/方法/复杂逻辑/重要变量必须中文注释
- **实现**: 禁止占位、临时跳过；功能需完整实现

#### 项目结构

- `src/main/java/com/company/plugin/`
  - `language/`: `ZyLanguage`、`ZyFileType`、`ZyParserDefinition`
  - `highlighting/`: `ZyLexer`、`ZySyntaxHighlighter`、`ZySyntaxHighlighterFactory`、`ZyTokenTypes`
  - `navigation/`: 跳转与引用
  - `completion/`: 补全
- `src/main/resources/META-INF/plugin.xml`: 扩展点注册
- `build.gradle.kts`: 构建配置

#### 构建与运行

- 构建: `./gradlew build --offline`
- 清理: `./gradlew clean`
- 沙箱运行: `./gradlew runIde`
- 缓存保护: 不删除全局 Gradle/Maven/IDEA 缓存；仅清理项目 `build/`、`out/`

#### 语法高亮与词法分析

- 入口组件：
  - 词法分析：`highlighting/ZyLexer`
  - Token 类型：`highlighting/ZyTokenTypes`
  - 高亮器：`highlighting/ZySyntaxHighlighter`
  - 颜色键：`highlighting/ZySyntaxHighlighterFactory`
- 关键字维护：
  - 在 `ZyLexer` 的 `ZY_KEYWORDS` 中增删（已包含 `struct`）
  - `getTokenTypeForText` 返回 `ZyTokenTypes.KEYWORD` → `ZySyntaxHighlighter` 映射至 `KEYWORD` 颜色
- 新语法元素流程：
  1. 在 `ZyLexer.advance()` 识别并返回新的 `ZyTokenTypes` 类型
  2. 在 `ZyTokenTypes` 定义 `IElementType`
  3. 在 `ZySyntaxHighlighter.getTokenHighlights` 做颜色映射
  4. 在 `ZySyntaxHighlighterFactory` 定义默认 `TextAttributesKey`
- 注意点：
  - 字符串转义与闭合、注释块闭合、变量标识（如 `$`）要完整处理
  - 空白应作为独立 token，保持 token 流连续

#### 代码补全与导航

- 补全：`completion/ZyCompletionContributor`
  - 基于上下文/索引提供候选，保证非阻塞；正确处理 `ProcessCanceledException`
- 跳转：`navigation/ZySimpleGotoDeclarationHandler`
- 引用：`navigation/ZySimpleReferenceContributor`
- 索引/作用域：`index/ZyScopeBasedParser` 可用于构建符号表，支持跨文件

#### plugin.xml 配置

- 注册：`fileType`、`syntaxHighlighterFactory`、`completion`、`gotoDeclarationHandler`、`referenceContributor` 等扩展点
- 确保 `.zy` 文件类型关联与图标配置

#### 性能与稳定性

- 性能：
  - 词法/高亮单次扫描，编辑区增量触发
  - 对索引/作用域解析结果做缓存，变更时精准失效
- 错误处理：
  - 捕获并传播 `ProcessCanceledException`，避免吞异常
  - 提供清晰日志与用户提示，不阻塞 UI 线程

#### 测试建议

- 单元测试：
  - 词法切分、高亮映射
  - 补全过滤/排序
  - 跳转/引用解析
- 集成测试：
  - 打开 `.zy` 文件全链路（高亮、补全、跳转）
- 查看报告：`build/reports/tests`

#### 典型改动清单

- 新增保留字：
  1. `ZyLexer.ZY_KEYWORDS.add("keyword")`
  2. 如需差异化配色：扩展 `ZyTokenTypes` 与颜色键
- 扩展补全：
  1. 在 `ZyCompletionContributor` 注入 `CompletionProvider`
  2. 从索引/作用域读取符号，排序与去重

#### 构建配置要点

- 使用 Java 插件与固定 IntelliJ 平台版本
- 配置本地 IDEA 路径、国内镜像与离线/缓存（见 `gradle.properties`）

#### 发布与验证

- 打包: `./gradlew build` 产出 `build/distributions/*.zip`
- 本地安装: IDE 安装 zip 或 `runIde` 沙箱验证
- 版本规范: 语义化版本，更新 `plugin.xml` 的兼容范围
