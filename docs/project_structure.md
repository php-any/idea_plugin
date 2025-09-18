### 项目目录结构与职责说明

#### 根目录

- `build.gradle.kts`：Gradle 构建脚本（Java 插件、IntelliJ 平台配置、依赖与任务）
- `settings.gradle.kts`：Gradle 工程设置（项目名、包含模块）
- `gradle.properties`：构建性能与缓存相关配置
- `gradlew` / `gradle/wrapper/`：Gradle Wrapper（固定构建环境，避免全局依赖）
- `init.gradle`：本地初始化/仓库镜像等定制配置
- `README.md`：项目说明（可补充安装与使用）

#### 源码目录

- `src/main/java/com/company/plugin/`：插件核心 Java 源码

  - `language/`：语言与文件类型定义
    - `ZyLanguage`：定义 ZY 语言类型
    - `ZyFileType`：定义 `.zy` 文件类型/图标/描述
    - `ZyParserDefinition`：PSI/解析相关定义入口
  - `highlighting/`：语法高亮与词法分析
    - `ZyLexer`：词法分析器（标记关键字/字符串/注释等）
    - `ZySyntaxHighlighter`：将 Token 类型映射到颜色键
    - `ZySyntaxHighlighterFactory`：注册颜色键与默认配色
    - `ZyTokenTypes`：定义语法元素的 `IElementType`
  - `completion/`：代码补全
    - `ZyCompletionContributor`：根据上下文提供补全建议
  - `navigation/`：代码导航与引用
    - `ZySimpleGotoDeclarationHandler`：跳转到定义
    - `ZySimpleReferenceContributor`：引用解析与高亮
  - `index/`：索引/作用域解析
    - `ZyScopeBasedParser`：基于作用域的简单解析，用于符号索引

- `src/main/resources/`：插件资源与声明

  - `META-INF/plugin.xml`：插件扩展点注册（文件类型、高亮、补全、导航等）
  - `icons/`：插件与文件类型图标资源（如 `zy-file.svg`）
  - `fileTypes/`：文件类型相关资源（如文件类型描述 XML）

- `src/test/java/`：Java 测试代码（单测/集成测试）

#### 测试与样例

- `test/`：示例 `.zy` 源文件（功能验证/测试输入）
  - `hello.zy`、`test.zy`、`user.zy` 等：通用示例
  - `model/`、`logic/` 子目录：按场景分类的示例代码

#### 构建输出

- `build/`：Gradle 构建产物与 IDEA 沙箱
  - `distributions/`：打包后的插件 ZIP（可用于安装/发布）
  - `idea-sandbox/`：运行 `runIde` 时的沙箱环境（不会影响本机 IDE）
  - 其他子目录：编译产物、测试报告、临时文件等

#### 约定与注意事项

- 所有类/方法/复杂逻辑/重要变量需中文注释，保持与实现一致
- 功能需完整实现，禁止占位或临时跳过逻辑
- 不删除全局缓存目录（Gradle/Maven/IDEA）；仅清理项目 `build/`、`out/`
- 新增语法/高亮/补全/导航时，分别在对应子包内扩展并在 `plugin.xml` 注册
