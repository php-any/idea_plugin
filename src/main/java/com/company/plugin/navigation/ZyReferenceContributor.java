package com.company.plugin.navigation;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * ZY 引用贡献器
 * 基于纯文本 PSI，为 .zy 文件中的标识符（[A-Za-z0-9_]+）提供精确的引用范围
 * 目的：让 Alt 悬停下划线仅覆盖标识符范围，而不是整文件
 */
public class ZyReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        // 在 ZY 语言文件的纯文本元素上提供引用
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiElement.class).inFile(PlatformPatterns.psiFile()),
            new PsiReferenceProvider() {
                @Override
                public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                    VirtualFile file = element.getContainingFile() != null ? element.getContainingFile().getVirtualFile() : null;
                    if (file == null || !file.getName().endsWith(".zy")) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    String text = element.getText();
                    if (text == null || text.trim().isEmpty()) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    // 按行切分，避免跨行引用导致整段文本被处理
                    java.util.List<PsiReference> references = new java.util.ArrayList<>();
                    int base = 0;
                    String[] lines = text.split("\n");
                    for (String line : lines) {
                        int i = 0;
                        while (i < line.length()) {
                            while (i < line.length() && !isWordChar(line.charAt(i))) i++;
                            int start = i;
                            while (i < line.length() && isWordChar(line.charAt(i))) i++;
                            int end = i;
                            if (end > start) {
                                TextRange range = new TextRange(base + start, base + end);
                                references.add(new ZyWordReference(element, range));
                            }
                        }
                        base += line.length() + 1;
                    }

                    return references.toArray(new PsiReference[0]);
                }
            }
        );
    }

    private boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}

/**
 * ZY 标识符引用
 * 提供基本的解析功能，支持函数调用和类引用的跳转
 */
class ZyWordReference extends PsiReferenceBase<PsiElement> {
    
    private final TextRange rangeInElement;

    public ZyWordReference(@NotNull PsiElement element, @NotNull TextRange rangeInElement) {
        super(element, rangeInElement, false);
        this.rangeInElement = rangeInElement;
    }

    @Override
    public PsiElement resolve() {
        // 尝试解析引用
        String word = getElement().getText().substring(rangeInElement.getStartOffset(), rangeInElement.getEndOffset());
        com.intellij.openapi.diagnostic.Logger.getInstance("ZyReferenceContributor").info("Resolving reference for word: '" + word + "'");
        
        // 在当前文件中查找定义
        PsiElement file = getElement().getContainingFile();
        if (file != null) {
            PsiElement target = findDefinitionInFile(file, word);
            if (target != null) {
                com.intellij.openapi.diagnostic.Logger.getInstance("ZyReferenceContributor").info("Found target: " + target.getClass().getSimpleName() + " at offset " + target.getTextOffset());
                return target;
            } else {
                com.intellij.openapi.diagnostic.Logger.getInstance("ZyReferenceContributor").info("No target found for word: '" + word + "'");
            }
        }
        
        return null;
    }

    @Override
    public Object @NotNull [] getVariants() {
        return new Object[0];
    }

    @Override
    @NotNull
    public TextRange getRangeInElement() {
        return rangeInElement;
    }
    
    /**
     * 在文件中查找定义
     */
    private PsiElement findDefinitionInFile(PsiElement file, String word) {
        String fileText = file.getText();
        if (fileText == null) return null;
        
        // 查找函数定义
        String functionPattern = "function\\s+(" + java.util.regex.Pattern.quote(word) + ")\\s*\\(";
        java.util.regex.Pattern functionRegex = java.util.regex.Pattern.compile(functionPattern);
        java.util.regex.Matcher functionMatcher = functionRegex.matcher(fileText);
        
        if (functionMatcher.find()) {
            int start = functionMatcher.start(1);
            return file.findElementAt(start);
        }
        
        // 查找类定义
        String classPattern = "class\\s+(" + java.util.regex.Pattern.quote(word) + ")\\s*[\\{]";
        java.util.regex.Pattern classRegex = java.util.regex.Pattern.compile(classPattern);
        java.util.regex.Matcher classMatcher = classRegex.matcher(fileText);
        
        if (classMatcher.find()) {
            int start = classMatcher.start(1);
            return file.findElementAt(start);
        }
        
        // 查找变量定义
        String varPattern = "(var|let|const)\\s+(" + java.util.regex.Pattern.quote(word) + ")\\s*[=;]";
        java.util.regex.Pattern varRegex = java.util.regex.Pattern.compile(varPattern);
        java.util.regex.Matcher varMatcher = varRegex.matcher(fileText);
        
        if (varMatcher.find()) {
            int start = varMatcher.start(2);
            return file.findElementAt(start);
        }
        
        return null;
    }
}
