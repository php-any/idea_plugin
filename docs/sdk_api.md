### IntelliJ Platform SDK 与交互 API 速查（语言插件）

#### 基础类型与文件关联

- `com.intellij.lang.Language`：定义语言对象（例如 `ZyLanguage`）
- `com.intellij.openapi.fileTypes.LanguageFileType`：定义文件类型（扩展名、图标、描述）
- `com.intellij.openapi.vfs.VirtualFile`：虚拟文件系统抽象
- `com.intellij.openapi.project.Project`：工程上下文
- 扩展点（`plugin.xml`）：
  - `com.intellij.fileType`：注册文件类型

#### 词法与高亮

- 词法分析
  - `com.intellij.lexer.LexerBase`：自定义 Lexer 基类（如 `ZyLexer`）
  - 关键方法：`start()`、`advance()`、`getTokenType()`、`getTokenStart()`、`getTokenEnd()`
  - Token 类型：`com.intellij.psi.tree.IElementType`（集中定义于 `ZyTokenTypes`）
- 语法高亮
  - `com.intellij.openapi.fileTypes.SyntaxHighlighter` / `SyntaxHighlighterBase`
  - `com.intellij.openapi.fileTypes.SyntaxHighlighterFactory`：工厂注册（如 `ZySyntaxHighlighterFactory`）
  - 颜色键：`com.intellij.openapi.editor.colors.TextAttributesKey`
  - 默认颜色映射：`com.intellij.openapi.editor.DefaultLanguageHighlighterColors`
- 扩展点：
  - `com.intellij.lang.syntaxHighlighterFactory`：注册高亮工厂

#### 解析与 PSI（如需）

- `com.intellij.lang.ParserDefinition`：语法/PSI 定义入口（`ZyParserDefinition`）
- `com.intellij.psi.PsiElement` / `PsiFile`：PSI 节点与文件
- 说明：本项目以轻量实现为主，若扩展 PSI，请确保解析与索引性能
- 扩展点：
  - `com.intellij.lang.parserDefinition`

#### 代码补全

- 入口
  - `com.intellij.codeInsight.completion.CompletionContributor`
  - `com.intellij.codeInsight.completion.CompletionProvider`
  - 上下文与结果：`CompletionParameters`、`CompletionResultSet`
- 推荐实践
  - 避免重活阻塞 UI；必要时缓存或快速判空返回
  - 正确处理 `com.intellij.openapi.progress.ProcessCanceledException`
- 扩展点：
  - `com.intellij.completion.contributor`

#### 跳转与引用

- 跳转到定义
  - `com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler`（如 `ZySimpleGotoDeclarationHandler`）
  - 返回目标：`PsiElement[]`，可根据标识符查找定义处
- 引用解析
  - `com.intellij.psi.PsiReference`、`PsiReferenceBase`
  - `com.intellij.lang.refactoring.RefactoringSupportProvider`（如需重构支持）
  - 引用贡献器：`com.intellij.psi.PsiReferenceContributor`（如 `ZySimpleReferenceContributor`）
- 扩展点：
  - `com.intellij.gotoDeclarationHandler`
  - `com.intellij.psi.referenceContributor`

#### 索引与搜索（可选增强）

- 文件索引：`com.intellij.util.indexing.FileBasedIndex`
- Stub 索引：`com.intellij.psi.stubs.StubIndex`
- Dumb 模式：`com.intellij.openapi.project.DumbService`（索引中不可用 API 的降级与延迟）

#### 编辑器/文档交互

- 文本与编辑
  - `com.intellij.openapi.editor.Editor`
  - `com.intellij.openapi.editor.Document`
  - `com.intellij.psi.PsiDocumentManager`：Document 与 PSI 同步
- 读写动作
  - 只读：`com.intellij.openapi.application.ReadAction`
  - 写入：`com.intellij.openapi.application.WriteAction`
  - 应用环境：`com.intellij.openapi.application.ApplicationManager`

#### 运行时与取消

- 进度与取消
  - `com.intellij.openapi.progress.ProgressManager`
  - `com.intellij.openapi.progress.ProcessCanceledException`（必须向上抛出或尽快返回）
- 后台任务（如需）
  - `com.intellij.openapi.progress.Task.Backgroundable`

#### 颜色与主题

- 颜色键：`TextAttributesKey`
- 默认颜色：`DefaultLanguageHighlighterColors`（关键字、字符串、数字、注释、操作符等）
- 主题适配：依赖 IDE 颜色方案，用户可在设置中覆盖

#### plugin.xml 常用扩展点示例（节选）

```xml
<idea-plugin>
  <extensions defaultExtensionNs="com.intellij">
    <fileType implementationClass="com.company.plugin.language.ZyFileType" />

    <syntaxHighlighterFactory
        language="ZY"
        implementationClass="com.company.plugin.highlighting.ZySyntaxHighlighterFactory"/>

    <completion.contributor
        language="ZY"
        implementationClass="com.company.plugin.completion.ZyCompletionContributor"/>

    <gotoDeclarationHandler
        implementation="com.company.plugin.navigation.ZySimpleGotoDeclarationHandler"/>

    <psi.referenceContributor
        implementation="com.company.plugin.navigation.ZySimpleReferenceContributor"/>
  </extensions>
</idea-plugin>
```

#### 开发注意事项

- 性能优先：Lexer 单次扫描、补全/跳转快速路径、必要时缓存
- 线程模型：避免阻塞 UI 线程；读/写动作用 `ReadAction`/`WriteAction`
- Dumb 模式：在索引重建时避免调用受限 API；使用 `DumbService` 检查或延迟
- 取消传播：捕获 `ProcessCanceledException` 立即返回，不吞异常
- 本地化注释：类/方法/复杂逻辑/重要变量必须中文注释，与实现保持一致

#### 典型调用片段（简化示意）

```java
// 高亮器提供 Lexer
public class ZySyntaxHighlighter extends SyntaxHighlighterBase {
    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
        return new ZyLexer();
    }
}
```

```java
// 补全贡献器（基于上下文注入候选）
public class ZyCompletionContributor extends CompletionContributor {
    public ZyCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<CompletionParameters>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters parameters,
                                          @NotNull ProcessingContext context,
                                          @NotNull CompletionResultSet result) {
                // 快速判空/判上下文，必要时返回
                // result.addElement(LookupElementBuilder.create("keyword"));
            }
        });
    }
}
```

#### 参考清单（类名/包名）

- 语言/文件：`Language`、`LanguageFileType`、`VirtualFile`、`Project`
- 词法/高亮：`LexerBase`、`IElementType`、`SyntaxHighlighter(Base)`、`SyntaxHighlighterFactory`、`TextAttributesKey`
- PSI/解析：`ParserDefinition`、`PsiElement`、`PsiFile`
- 补全：`CompletionContributor`、`CompletionProvider`、`CompletionParameters`、`CompletionResultSet`
- 导航/引用：`GotoDeclarationHandler`、`PsiReference`、`PsiReferenceContributor`
- 索引：`FileBasedIndex`、`StubIndex`、`DumbService`
- 编辑器：`Editor`、`Document`、`PsiDocumentManager`
- 并发：`ReadAction`、`WriteAction`、`ProgressManager`、`ProcessCanceledException`
