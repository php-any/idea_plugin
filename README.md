# ZY Language Support (GoLand/IDEA 插件)

提供 Origami/折言 语言（.cjp, .cj）语法高亮与基础补全（含中文关键字、注解）。未来可扩展接入 LSP（例如 `zy-lsp`）。

## 功能

- 语法高亮：关键字/字符串/数字/注释
- 基础补全：英文/中文关键字与常用注解（@Controller、@Route、@GetMapping、@Inject）
- 文件类型：`.cjp`、`.cj`、`.zy`

参考 VSCode 实现：`php-any/zy-extension`，地址见仓库说明。

## 开发与运行

1. 安装 JDK 17 与 Gradle（推荐使用 `gradle wrapper`，后续可添加）。
2. 在项目根目录执行：
   ```bash
   ./gradlew build
   ./gradlew runIde
   ```
3. 在沙箱 IDE 中创建 `.cjp` 文件，即可看到高亮与补全。

## 后续计划

- LSP 接入占位：支持 stdio/TCP 两种方式，与 VSCode 版一致的优先级与回退逻辑。
- 更完备的词法/语法解析与 PSI 结构。
