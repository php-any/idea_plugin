package com.company.plugin.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;

import java.util.*;

/**
 * ZY 跳转到定义处理器
 * 基于本地 Java 实现，不依赖 LSP
 */
public class ZyGotoDeclarationHandler implements GotoDeclarationHandler {

    private static final Logger LOG = Logger.getInstance(ZyGotoDeclarationHandler.class);

    /**
     * 获取下划线显示的目标元素
     * 这个方法控制 Alt + 光标悬停时显示下划线的范围
     */
    @Override
    @Nullable
    public PsiElement[] getGotoDeclarationTargets(PsiElement element, int offset, Editor editor) {
        Project project = editor.getProject();
        if (project == null) return null;
        
        // 当传入的是文件级元素（整文件）时，直接返回 null，避免整文件下划线
        if (element == null || element instanceof com.intellij.psi.PsiFile) {
            return null;
        }
        
        VirtualFile file = element.getContainingFile() != null ? element.getContainingFile().getVirtualFile() : null;
        if (file == null || !file.getName().endsWith(".zy")) {
            return null;
        }

        // 先进行词法级"单词"范围判定（字母、数字、下划线）
        TextRange wordRange = computeWordRange(editor, offset);
        if (wordRange == null) {
            var doc = editor.getDocument();
            int[] tryOffsets = {
                Math.max(offset - 1, 0),
                Math.min(offset + 1, doc.getTextLength())
            };
            for (int off : tryOffsets) {
                wordRange = computeWordRange(editor, off);
                if (wordRange != null) break;
            }
            if (wordRange == null) {
                wordRange = new TextRange(
                    Math.max(offset, 0),
                    Math.min(offset + 1, editor.getDocument().getTextLength())
                );
                LOG.info("GTD: no word found at caret; use minimal range=" + wordRange);
            }
        }
        
        // 获取光标位置处的精确叶子元素，确保仅在标识符等可导航单元上生效
        PsiElement leafAtCaret = element.getContainingFile().findElementAt(offset);
        if (leafAtCaret == null || leafAtCaret.getTextLength() == 0 || leafAtCaret.getText().trim().isEmpty()) {
            return null;
        }
        
        LOG.info("GTD: leaf=" + leafAtCaret.getClass().getSimpleName() + 
                " text='" + leafAtCaret.getText() + "' range=" + leafAtCaret.getTextRange() + 
                " wordRange=" + wordRange);

        // 引用命中策略：
        // 1) 优先检查叶子元素上的引用
        // 2) 若未命中，向上检查父级元素（最多 3 层），很多语言的调用/成员访问引用挂在父节点
        boolean refHit = checkReferenceHit(leafAtCaret, offset);
        if (!refHit) {
            PsiElement p = leafAtCaret.getParent();
            int depth = 0;
            while (!refHit && p != null && depth < 3) {
                if (checkReferenceHit(p, offset)) {
                    refHit = true;
                } else {
                    p = p.getParent();
                    depth++;
                }
            }
        }
        
        if (refHit) {
            LOG.info("GTD: reference range hit; continue to local lookup");
        } else {
            LOG.info("GTD: no reference hit on leaf/parents; fallback to word-based local lookup");
        }

        try {
            List<PsiElement> targets = new ArrayList<>();
            
            // 基于本地实现的定义查找
            String word = wordRange.substring(leafAtCaret.getContainingFile().getText());
            LOG.info("GTD: searching for word: '" + word + "' at range: " + wordRange);

            // 属性访问优先：当检测到 ->name 且后面不是 '(' 时，仅按属性解析，优先从命名空间 JSON 索引命中 property
            if (isPropertyAccessContext(editor, wordRange)) {
                String ns = extractNamespace(leafAtCaret.getContainingFile().getText());
                String cls = extractClassNameForPropertyChain(editor.getDocument().getCharsSequence(), wordRange.getStartOffset());
                List<PsiElement> propTargets = findPropertyFromNamespaceIndex(project, ns, cls, word, leafAtCaret.getContainingFile().getVirtualFile(), wordRange.getStartOffset(), wordRange.getEndOffset());
                if (!propTargets.isEmpty()) {
                    return propTargets.toArray(new PsiElement[0]);
                }
            }
            
            // 在当前文件中查找定义，传入需要跳过的当前位置范围，避免把自身加入候选
            List<PsiElement> localTargets = findLocalDefinitions(leafAtCaret, word, wordRange.getStartOffset(), wordRange.getEndOffset());
            targets.addAll(localTargets);
            LOG.info("GTD: found " + localTargets.size() + " local definitions");
            
            // 如果本地没有找到，尝试跨文件搜索
            if (targets.isEmpty()) {
                // 分析上下文，检查是否是成员访问
                String contextClassName = analyzeContextForMemberAccess(leafAtCaret, word);
                LOG.info("GTD: searching for word '" + word + "', context class name is '" + contextClassName + "'");
                
                // 如果有上下文类名，优先在该类中搜索
                if (contextClassName != null && !contextClassName.isEmpty()) {
                    boolean isMethodCall = isMethodCallContext(leafAtCaret, word);
                    LOG.info("GTD: context analysis - class: '" + contextClassName + "', word: '" + word + "', isMethodCall: " + isMethodCall);
                    List<PsiElement> contextTargets = findInSpecificClass(project, contextClassName, word, file, isMethodCall);
                    targets.addAll(contextTargets);

                    // 方法调用时，额外并入跨命名空间的同名方法候选，避免只显示一侧
                    if (isMethodCall) {
                        List<PsiElement> extra = findCrossFileDefinitions(project, word, file);
                        java.util.Set<String> sig = new java.util.HashSet<>();
                        for (PsiElement t : targets) {
                            var vf0 = t.getContainingFile() != null ? t.getContainingFile().getVirtualFile() : null;
                            if (vf0 != null) sig.add(vf0.getPath() + "@" + t.getTextOffset());
                        }
                        for (PsiElement t : extra) {
                            var vf = t.getContainingFile() != null ? t.getContainingFile().getVirtualFile() : null;
                            String key = vf != null ? vf.getPath() + "@" + t.getTextOffset() : null;
                            if (key != null && !sig.contains(key)) {
                                targets.add(t);
                            }
                        }
                    }
                }
                
                // 如果上下文搜索没有结果，尝试常规搜索
                if (targets.isEmpty()) {
                    // 解析 use 语句，获取真实的类名
                    String realClassName = parseUseStatementsAndGetRealClassName(leafAtCaret.getContainingFile().getText(), word);
                    LOG.info("GTD: searching for word '" + word + "', real class name is '" + realClassName + "'");
                    
                    // 首先尝试搜索原始单词
                    List<PsiElement> crossFileTargets = findCrossFileDefinitions(project, word, file);
                    LOG.info("GTD: found " + crossFileTargets.size() + " cross-file definitions for original word '" + word + "'");
                    
                    // 如果原始单词没找到，且真实类名不同，尝试搜索真实类名
                    if (crossFileTargets.isEmpty() && !word.equals(realClassName)) {
                        LOG.info("GTD: trying to find real class name '" + realClassName + "'");
                        
                        // 从完全限定名中提取类名和命名空间路径
                        String[] parts = realClassName.split("\\\\");
                        String actualClassName = parts.length > 0 ? parts[parts.length - 1] : realClassName;
                        String namespacePath = parts.length > 1 ? String.join("/", java.util.Arrays.copyOf(parts, parts.length - 1)) : null;
                        
                        LOG.info("GTD: extracted actual class name '" + actualClassName + "' and namespace path '" + namespacePath + "' from FQN '" + realClassName + "'");
                        
                        crossFileTargets = findCrossFileDefinitions(project, actualClassName, namespacePath, file);
                        LOG.info("GTD: found " + crossFileTargets.size() + " cross-file definitions for actual class name '" + actualClassName + "' with namespace path '" + namespacePath + "'");
                    }
                    
                    targets.addAll(crossFileTargets);
                }
            }
            
            // 如果还是没有找到，尝试查找引用（过滤自身范围）
            if (targets.isEmpty()) {
                List<PsiElement> referenceTargets = findReferences(leafAtCaret, word, wordRange.getStartOffset(), wordRange.getEndOffset());
                targets.addAll(referenceTargets);
                LOG.info("GTD: found " + referenceTargets.size() + " references");
            }
            
            LOG.info("GTD: total targets=" + targets.size());
            
            if (!targets.isEmpty()) {
                for (int i = 0; i < targets.size(); i++) {
                    PsiElement target = targets.get(i);
                    LOG.info("GTD: target[" + i + "]=" + target.getClass().getSimpleName() + 
                            " text='" + target.getText() + "' offset=" + target.getTextOffset());
                }
            }
            
            return targets.isEmpty() ? null : targets.toArray(new PsiElement[0]);
        } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
            // 重新抛出控制流异常，不应该记录日志
            throw e;
        } catch (Exception e) {
            LOG.error("Local definition request failed", e);
            return null;
        }
    }

    @Override
    @Nullable
    public String getActionText(DataContext context) {
        return null;
    }

    /**
     * 检查引用命中
     */
    private boolean checkReferenceHit(PsiElement elem, int caretAbsOffset) {
        TextRange elemRange = elem.getTextRange();
        if (elemRange == null) return false;
        
        int rel = caretAbsOffset - elemRange.getStartOffset();
        List<PsiReference> refs = PsiReferenceService.getService().getReferences(elem, PsiReferenceService.Hints.NO_HINTS);
        
        if (!refs.isEmpty()) {
            for (PsiReference r : refs) {
                if (rel >= r.getRangeInElement().getStartOffset() && 
                    rel <= r.getRangeInElement().getEndOffset()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 计算光标处的"单词"范围（[A-Za-z0-9_]+）。若不存在则返回 null。
     */
    @Nullable
    private TextRange computeWordRange(Editor editor, int caretOffset) {
        CharSequence chars = editor.getDocument().getCharsSequence();
        if (chars.length() == 0) return null;
        
        int length = chars.length();
        int idx = Math.max(0, Math.min(caretOffset, length));

        // 内联方法，避免语法错误

        int start = idx;
        int end = idx;
        while (start > 0 && (Character.isLetterOrDigit(chars.charAt(start - 1)) || chars.charAt(start - 1) == '_')) start--;
        while (end < length && (Character.isLetterOrDigit(chars.charAt(end)) || chars.charAt(end) == '_')) end++;

        if (start >= end) return null;
        TextRange range = new TextRange(start, end);
        LOG.debug("computeWordRange: caret=" + caretOffset + " -> " + range + 
                 ", text='" + chars.subSequence(start, end) + "'");
        return range;
    }

    /**
     * 在当前文件中查找本地定义
     */
    private List<PsiElement> findLocalDefinitions(PsiElement context, String word, int skipStart, int skipEnd) {
        List<PsiElement> targets = new ArrayList<>();
        
        try {
            // 遍历文件中的所有元素，查找可能的定义
            PsiElement file = context.getContainingFile();
            if (file != null) {
                // 优先使用索引：限定当前文件路径的命中，快速定位偏移
                try {
                    Project project = file.getProject();
                    com.company.plugin.index.ZySymbolIndexService index = com.company.plugin.index.ZySymbolIndexService.getInstance(project);
                    index.ensureUpToDate();
                    java.util.List<com.company.plugin.index.ZySymbolIndexService.LocationState> locations = index.findDefinitions(word, null);
                    if (!locations.isEmpty()) {
                        String currentPath = file.getContainingFile().getVirtualFile().getPath();
                        for (com.company.plugin.index.ZySymbolIndexService.LocationState ls : locations) {
                            if (currentPath.equals(ls.filePath)) {
                                // 跳过自身：若命中的偏移位于当前点击单词范围内，则忽略
                                if (ls.offset >= skipStart && ls.offset < skipEnd) {
                                    continue;
                                }
                                PsiElement targetElement = file.findElementAt(ls.offset);
                                if (targetElement != null) {
                                    targets.add(createNavigationElement(targetElement,
                                            file.getContainingFile().getVirtualFile(),
                                            ls.offset,
                                            new TextRange(ls.offset, ls.offset + Math.max(1, word.length()))));
                                }
                            }
                        }
                        if (!targets.isEmpty()) {
                            return targets; // 命中当前文件则直接返回
                        }
                    }
                } catch (Throwable ignore) {}

                findDefinitionsInElementSkipping(file, word, targets, skipStart, skipEnd);
                
                // 如果没有找到定义，尝试查找函数调用
                if (targets.isEmpty()) {
                    findFunctionCalls(file, word, targets);
                }
                
                // 如果还是没有找到，尝试查找类定义
                if (targets.isEmpty()) {
                    findClassDefinitions(file, word, targets);
                }
            }
        } catch (Exception e) {
            LOG.debug("Error finding local definitions", e);
        }
        
        return targets;
    }

    /**
     * 在元素中递归查找定义
     */
    private void findDefinitionsInElementSkipping(PsiElement element, String word, List<PsiElement> targets, int skipStart, int skipEnd) {
        if (element == null) return;
        
        // 检查当前元素是否匹配
        String text = element.getText();
        if (text != null && text.equals(word)) {
            // 检查是否是定义（函数、变量、类等）
            if (isDefinition(element)) {
                int defOffset = element.getTextOffset();
                // 跳过当前位置自身
                if (defOffset < skipStart || defOffset >= skipEnd) {
                    targets.add(createNavigationElement(element, element.getContainingFile().getVirtualFile(), 
                                                      defOffset, element.getTextRange()));
                }
            }
        }
        
        // 递归检查子元素
        for (PsiElement child : element.getChildren()) {
            findDefinitionsInElementSkipping(child, word, targets, skipStart, skipEnd);
        }
    }

    /**
     * 查找引用
     */
    private List<PsiElement> findReferences(PsiElement context, String word, int skipStart, int skipEnd) {
        List<PsiElement> targets = new ArrayList<>();
        
        try {
            PsiElement file = context.getContainingFile();
            if (file != null) {
                findReferencesInElementSkipping(file, word, targets, skipStart, skipEnd);
            }
        } catch (Exception e) {
            LOG.debug("Error finding references", e);
        }
        
        return targets;
    }

    /**
     * 在元素中递归查找引用
     */
    private void findReferencesInElementSkipping(PsiElement element, String word, List<PsiElement> targets, int skipStart, int skipEnd) {
        if (element == null) return;
        
        // 检查当前元素是否匹配
        String text = element.getText();
        if (text != null && text.equals(word)) {
            int off = element.getTextOffset();
            if (off < skipStart || off >= skipEnd) {
                targets.add(createNavigationElement(element, element.getContainingFile().getVirtualFile(), 
                                                  off, element.getTextRange()));
            }
        }
        
        // 递归检查子元素
        for (PsiElement child : element.getChildren()) {
            findReferencesInElementSkipping(child, word, targets, skipStart, skipEnd);
        }
    }

    /**
     * 查找函数调用
     */
    private void findFunctionCalls(PsiElement file, String word, List<PsiElement> targets) {
        String fileText = file.getText();
        if (fileText == null) return;
        
        // 查找函数定义模式：function functionName(...)
        String pattern = "function\\s+(" + java.util.regex.Pattern.quote(word) + ")\\s*\\(";
        java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher matcher = regex.matcher(fileText);
        
        while (matcher.find()) {
            int start = matcher.start(1);
            int end = matcher.end(1);
            
            LOG.info("GTD: Found function definition at " + start + "-" + end + ": " + fileText.substring(start, end));
            
            // 创建导航元素
            PsiElement targetElement = file.findElementAt(start);
            if (targetElement != null) {
                targets.add(createNavigationElement(targetElement, 
                    file.getContainingFile().getVirtualFile(), 
                    start, 
                    new TextRange(start, end)));
            }
        }
    }
    
    /**
     * 查找类定义
     */
    private void findClassDefinitions(PsiElement file, String word, List<PsiElement> targets) {
        String fileText = file.getText();
        if (fileText == null) return;
        
        // 查找类定义模式：class ClassName
        String pattern = "class\\s+(" + java.util.regex.Pattern.quote(word) + ")\\s*[\\{]";
        java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher matcher = regex.matcher(fileText);
        
        while (matcher.find()) {
            int start = matcher.start(1);
            int end = matcher.end(1);
            
            // 创建导航元素
            PsiElement targetElement = file.findElementAt(start);
            if (targetElement != null) {
                targets.add(createNavigationElement(targetElement, 
                    file.getContainingFile().getVirtualFile(), 
                    start, 
                    new TextRange(start, end)));
            }
        }
    }
    
    /**
     * 检查元素是否是定义
     */
    private boolean isDefinition(PsiElement element) {
        // 简单的定义检查逻辑
        // 在实际实现中，这里应该根据 ZY 语言的语法规则来判断
        PsiElement parent = element.getParent();
        if (parent == null) return false;
        
        String parentText = parent.getText();
        if (parentText == null) return false;
        
        String elementText = element.getText();
        if (elementText == null) return false;
        
        // 检查是否是函数定义的一部分: function functionName(...)
        if (parentText.startsWith("function " + elementText + "(") || 
            parentText.contains("function " + elementText + "(")) {
            return true;
        }
        
        // 检查是否是类定义的一部分: class ClassName
        if (parentText.startsWith("class " + elementText) || 
            parentText.contains("class " + elementText)) {
            return true;
        }
        
        // 检查是否是变量定义的一部分: var/let/const variableName
        if (parentText.startsWith("var " + elementText) || 
            parentText.contains("var " + elementText) ||
            parentText.startsWith("let " + elementText) || 
            parentText.contains("let " + elementText) ||
            parentText.startsWith("const " + elementText) || 
            parentText.contains("const " + elementText)) {
            return true;
        }
        
        return false;
    }

    /**
     * 创建导航元素（本地文件）
     */
    private PsiElement createNavigationElement(PsiElement element, VirtualFile file, int offset, TextRange range) {
        int safeOffset = Math.max(0, Math.min(offset, element.getContainingFile().getTextLength() - 1));
        String display = buildDisplayLabel(element.getProject(), element.getContainingFile(), safeOffset, element.getText());
        return new PresentedNavigationItem(element.getProject(), file, element.getContainingFile(), safeOffset, range, display);
    }

    /**
     * 跨文件搜索定义
     */
    private List<PsiElement> findCrossFileDefinitions(Project project, String word, VirtualFile currentFile) {
        return findCrossFileDefinitions(project, word, null, currentFile);
    }
    
    /**
     * 跨文件搜索定义（带命名空间路径）
     */
    private List<PsiElement> findCrossFileDefinitions(Project project, String word, String namespacePath, VirtualFile currentFile) {
        List<PsiElement> targets = new ArrayList<>();
        
        try {
            // 确保索引是最新的
            com.company.plugin.index.ZySymbolIndexService.getInstance(project).ensureUpToDate();
            
            // 使用内存索引缓存查找定义
            List<com.company.plugin.index.ZySymbolIndexService.LocationState> locations = 
                com.company.plugin.index.ZySymbolIndexService.getInstance(project).findDefinitions(word, namespacePath);
            
            if (!locations.isEmpty()) {
                LOG.info("GTD: found " + locations.size() + " definitions in memory index for '" + word + "'");
                for (com.company.plugin.index.ZySymbolIndexService.LocationState location : locations) {
                    try {
                        // 检查是否被取消
                        com.intellij.openapi.progress.ProgressManager.checkCanceled();
                        
                        VirtualFile targetFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(location.filePath);
                        if (targetFile != null && !targetFile.equals(currentFile)) {
                            PsiElement target = createCrossFileNavigationElement(project, targetFile, location.offset, word);
                            if (target != null) {
                                targets.add(target);
                                LOG.info("GTD: added cross-file target: " + targetFile.getName() + ":" + location.offset);
                            }
                        }
                    } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
                        // 搜索被取消，返回已找到的结果
                        LOG.debug("Index search cancelled, returning " + targets.size() + " results found so far");
                        return targets;
                    }
                }
            }
            
            // 仅使用索引结果，不回退到全项目扫描
            
        } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
            // 搜索被取消，返回已找到的结果
            LOG.debug("Cross-file search cancelled, returning " + targets.size() + " results found so far");
            return targets;
        } catch (Exception e) {
            LOG.warn("Error in cross-file search", e);
        }
        
        return targets;
    }
    
    // 索引专用：移除全项目扫描回退
    
    /**
     * 创建跨文件导航元素
     */
    private PsiElement createCrossFileNavigationElement(Project project, VirtualFile file, int offset, String text) {
        try {
            com.intellij.psi.PsiFile psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(file);
            if (psiFile == null) {
                return null;
            }
            // 确保偏移量在有效范围内
            int safeOffset = Math.max(0, Math.min(offset, psiFile.getTextLength() - 1));
            com.intellij.openapi.util.TextRange range;
            PsiElement targetElement = psiFile.findElementAt(safeOffset);
            if (targetElement != null && targetElement.getTextRange() != null) {
                range = targetElement.getTextRange();
            } else {
                int len = Math.max(1, text != null ? text.length() : 1);
                range = new com.intellij.openapi.util.TextRange(safeOffset, Math.min(psiFile.getTextLength(), safeOffset + len));
            }
            String display = buildDisplayLabel(project, psiFile, safeOffset, text);
            return new PresentedNavigationItem(project, file, psiFile, safeOffset, range, display);
        } catch (Exception e) {
            LOG.debug("createCrossFileNavigationElement failed", e);
            return null;
        }
    }

    // 组装展示文本：优先 FQN（来自命名空间索引），否则 namespace\\word
    private String buildDisplayLabel(Project project, com.intellij.psi.PsiFile psiFile, int offset, String preferred) {
        String fqn = resolveFqnFromNamespaceIndex(project, psiFile, offset, preferred);
        if (fqn != null && !fqn.isEmpty()) return fqn;
        try {
            String ns = extractNamespace(psiFile.getText());
            String word = preferred != null && !preferred.isEmpty()
                ? preferred
                : psiFile.getText().substring(Math.max(0, offset), Math.min(psiFile.getTextLength(), offset + 32)).split("[^A-Za-z0-9_\\$]")[0];
            if (ns != null && !ns.isEmpty()) return ns + "\\" + word;
            return word;
        } catch (Throwable t) {
            return preferred != null ? preferred : "";
        }
    }

    // 相对路径
    private static String getRelativePath(Project project, VirtualFile vf) {
        try {
            String base = project.getBasePath();
            if (base == null) return vf.getPath();
            String p = vf.getPath();
            if (p.startsWith(base + "/")) return p.substring(base.length() + 1);
            return p;
        } catch (Throwable t) { return vf.getPath(); }
    }

    // 计算行号
    private static int computeLineNumber(com.intellij.psi.PsiFile psiFile, int offset) {
        try {
            com.intellij.openapi.editor.Document doc = com.intellij.psi.PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
            if (doc != null) return doc.getLineNumber(Math.max(0, Math.min(offset, doc.getTextLength())) ) + 1;
        } catch (Throwable ignored) {}
        return 1;
    }

    // 命名空间索引解析当前偏移的符号 FQN（用于展示）
    private String resolveFqnFromNamespaceIndex(Project project, com.intellij.psi.PsiFile psiFile, int offset, String word) {
        try {
            String ns = extractNamespace(psiFile.getText());
            if (ns == null || ns.isEmpty()) return null;
            com.company.plugin.index.ZyJsonIndexStore.DirIndex idx = com.company.plugin.index.ZyJsonIndexStore.readNamespaceIndex(project, ns);
            if (idx == null || idx.files == null) return null;
            String rel = getRelativePath(project, psiFile.getVirtualFile());
            for (com.company.plugin.index.ZyJsonIndexStore.FileEntry f : idx.files) {
                if (!rel.equals(f.path) || f.symbols == null) continue;
                com.company.plugin.index.ZyJsonIndexStore.SymbolEntry best = null; int bestDist = Integer.MAX_VALUE;
                for (com.company.plugin.index.ZyJsonIndexStore.SymbolEntry s : f.symbols) {
                    int dist = Math.abs(s.offset - offset);
                    if (dist < bestDist) { bestDist = dist; best = s; }
                }
                if (best != null && best.fqn != null && !best.fqn.isEmpty()) return best.fqn;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // 包装的可展示导航元素
    private static class PresentedNavigationItem extends com.intellij.psi.impl.FakePsiElement implements com.intellij.navigation.NavigationItem {
        private final Project project;
        private final VirtualFile file;
        private final com.intellij.psi.PsiFile psiFile;
        private final int offset;
        private final TextRange range;
        private final String display;

        PresentedNavigationItem(Project project, VirtualFile file, com.intellij.psi.PsiFile psiFile, int offset, TextRange range, String display) {
            this.project = project;
            this.file = file;
            this.psiFile = psiFile;
            this.offset = offset;
            this.range = range;
            this.display = display;
        }

        @Override public PsiElement getParent() { return psiFile; }
        @Override public com.intellij.psi.PsiFile getContainingFile() { return psiFile; }
        @Override public TextRange getTextRange() { return range; }
        @Override public void navigate(boolean requestFocus) {
            try {
                OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, offset);
                if (descriptor.canNavigate()) descriptor.navigate(requestFocus);
            } catch (Exception ignored) {}
        }
        @Override public boolean canNavigate() { return true; }
        @Override public boolean canNavigateToSource() { return true; }
        @Override public String getName() { return display; }
        @Override public String toString() { return display + " — " + getRelativePath(project, file) + ":" + computeLineNumber(psiFile, offset); }
        @Override public com.intellij.navigation.ItemPresentation getPresentation() {
            String location = getRelativePath(project, file) + ":" + Math.max(1, computeLineNumber(psiFile, offset));
            return new com.intellij.navigation.ItemPresentation() {
                @Override public String getPresentableText() { return display; }
                @Override public String getLocationString() { return location; }
                @Override public javax.swing.Icon getIcon(boolean unused) { return null; }
            };
        }
    }
    
    /**
     * 分析上下文，检查是否是成员访问
     * 按照语法分析逻辑：先分析对象类型，再分析成员访问
     */
    private String analyzeContextForMemberAccess(PsiElement element, String word) {
        try {
            // 获取当前元素的文本
            String elementText = element.getText();
            if (elementText == null || !elementText.equals(word)) {
                return null;
            }
            
            // 向上查找父元素，寻找成员访问模式
            PsiElement parent = element.getParent();
            int depth = 0;
            while (parent != null && depth < 5) {
                String parentText = parent.getText();
                if (parentText != null) {
                    // 检查是否是 new ClassName()->member 模式
                    java.util.regex.Pattern newPattern = java.util.regex.Pattern.compile("new\\s+(\\w+)\\s*\\(\\)\\s*->\\s*" + java.util.regex.Pattern.quote(word) + "\\b");
                    java.util.regex.Matcher matcher = newPattern.matcher(parentText);
                    if (matcher.find()) {
                        String className = matcher.group(1);
                        LOG.info("GTD: found new object pattern: new " + className + "()->" + word);
                        return className;
                    }
                    
                    // 检查是否是 variable->member 模式
                    java.util.regex.Pattern varPattern = java.util.regex.Pattern.compile("(\\w+)\\s*->\\s*" + java.util.regex.Pattern.quote(word) + "\\b");
                    matcher = varPattern.matcher(parentText);
                    if (matcher.find()) {
                        String varName = matcher.group(1);
                        LOG.info("GTD: found variable pattern: " + varName + "->" + word);
                        // 分析变量类型，返回实际的类名
                        return analyzeVariableType(varName, element.getContainingFile().getText());
                    }
                }
                
                parent = parent.getParent();
                depth++;
            }
        } catch (Exception e) {
            LOG.debug("Error analyzing context for member access", e);
        }
        
        return null;
    }
    
    /**
     * 分析变量类型，返回实际的类名
     */
    private String analyzeVariableType(String varName, String fileText) {
        try {
            // 查找变量赋值语句：$varName = new ClassName()
            java.util.regex.Pattern assignmentPattern = java.util.regex.Pattern.compile("\\$" + java.util.regex.Pattern.quote(varName) + "\\s*=\\s*new\\s+(\\w+)\\s*\\(\\)");
            java.util.regex.Matcher matcher = assignmentPattern.matcher(fileText);
            if (matcher.find()) {
                String className = matcher.group(1);
                LOG.info("GTD: found variable assignment: $" + varName + " = new " + className + "()");
                return className;
            }
            
            LOG.info("GTD: could not determine type for variable: $" + varName);
            return varName; // 如果无法确定类型，返回变量名本身
        } catch (Exception e) {
            LOG.debug("Error analyzing variable type", e);
            return varName;
        }
    }
    
    /**
     * 检查是否是方法调用上下文
     * 基于语法分析：检查是否有括号 () 表示方法调用
     */
    private boolean isMethodCallContext(PsiElement element, String word) {
        try {
            // 获取当前元素的文本
            String elementText = element.getText();
            if (elementText == null || !elementText.equals(word)) {
                return false;
            }
            
            LOG.info("GTD: checking method call context for word: '" + word + "'");
            System.out.println("DEBUG: checking method call context for word: '" + word + "'");
            
            // 向上查找父元素，寻找方法调用模式
            PsiElement parent = element.getParent();
            int depth = 0;
            while (parent != null && depth < 5) {
                String parentText = parent.getText();
                if (parentText != null) {
                    LOG.info("GTD: checking parent text: '" + parentText + "'");
                    System.out.println("DEBUG: checking parent text: '" + parentText + "'");
                    
                    // 检查是否包含括号，表示方法调用
                    // 模式：object->member(...) 或 new Class()->member(...)
                    java.util.regex.Pattern methodPattern = java.util.regex.Pattern.compile("\\w+\\s*->\\s*" + java.util.regex.Pattern.quote(word) + "\\s*\\(");
                    java.util.regex.Matcher matcher = methodPattern.matcher(parentText);
                    if (matcher.find()) {
                        LOG.info("GTD: detected method call context: " + parentText.substring(0, Math.min(50, parentText.length())));
                        System.out.println("DEBUG: detected method call context: " + parentText.substring(0, Math.min(50, parentText.length())));
                        return true;
                    }
                    
                    // 检查是否是 new Class()->member(...) 模式
                    java.util.regex.Pattern newMethodPattern = java.util.regex.Pattern.compile("new\\s+\\w+\\s*\\(\\)\\s*->\\s*" + java.util.regex.Pattern.quote(word) + "\\s*\\(");
                    matcher = newMethodPattern.matcher(parentText);
                    if (matcher.find()) {
                        LOG.info("GTD: detected new object method call context: " + parentText.substring(0, Math.min(50, parentText.length())));
                        System.out.println("DEBUG: detected new object method call context: " + parentText.substring(0, Math.min(50, parentText.length())));
                        return true;
                    }
                }
                
                parent = parent.getParent();
                depth++;
            }
            
            LOG.info("GTD: no method call context detected for word: '" + word + "'");
            System.out.println("DEBUG: no method call context detected for word: '" + word + "'");
        } catch (Exception e) {
            LOG.debug("Error checking method call context", e);
        }
        
        return false;
    }
    
    /**
     * 在特定类中查找成员定义
     */
    private List<PsiElement> findInSpecificClass(Project project, String className, String memberName, VirtualFile currentFile) {
        return findInSpecificClass(project, className, memberName, currentFile, false);
    }
    
    /**
     * 在特定类中查找成员定义（带方法调用标识）
     */
    private List<PsiElement> findInSpecificClass(Project project, String className, String memberName, VirtualFile currentFile, boolean isMethodCall) {
        List<PsiElement> targets = new ArrayList<>();
        
        try {
            // 首先在当前文件中查找类定义
            PsiElement currentFileElement = com.intellij.psi.PsiManager.getInstance(project).findFile(currentFile);
            if (currentFileElement != null) {
                String fileText = currentFileElement.getText();
                if (fileText != null) {
                    // 查找类定义
                    String classPattern = "class\\s+(" + java.util.regex.Pattern.quote(className) + ")\\s*\\{";
                    java.util.regex.Pattern regex = java.util.regex.Pattern.compile(classPattern);
                    java.util.regex.Matcher matcher = regex.matcher(fileText);
                    
                    if (matcher.find()) {
                        int classStart = matcher.start(1);
                        LOG.info("GTD: found class '" + className + "' in current file at " + classStart);
                        
                        // 在类定义中查找成员
                        List<PsiElement> classMembers = findMembersInClass(fileText, classStart, memberName, currentFile, project, isMethodCall);
                        targets.addAll(classMembers);
                    }
                }
            }
            
            // 如果当前文件中没有找到，搜索其他文件
            if (targets.isEmpty()) {
                // 使用索引服务查找类定义
                com.company.plugin.index.ZySymbolIndexService.getInstance(project).ensureUpToDate();
                List<com.company.plugin.index.ZySymbolIndexService.LocationState> classLocations = 
                    com.company.plugin.index.ZySymbolIndexService.getInstance(project).findDefinitions(className, null);
                
                for (com.company.plugin.index.ZySymbolIndexService.LocationState location : classLocations) {
                    try {
                        VirtualFile classFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(location.filePath);
                        if (classFile != null && !classFile.equals(currentFile)) {
                            PsiElement classPsiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(classFile);
                            if (classPsiFile != null) {
                                String classFileText = classPsiFile.getText();
                                if (classFileText != null) {
                                    // 查找类定义
                                    String classPattern = "class\\s+(" + java.util.regex.Pattern.quote(className) + ")\\s*\\{";
                                    java.util.regex.Pattern regex = java.util.regex.Pattern.compile(classPattern);
                                    java.util.regex.Matcher matcher = regex.matcher(classFileText);
                                    
                                    if (matcher.find()) {
                                        int classStart = matcher.start(1);
                                        LOG.info("GTD: found class '" + className + "' in " + classFile.getName() + " at " + classStart);
                                        
                                        // 在类定义中查找成员
                                        List<PsiElement> classMembers = findMembersInClass(classFileText, classStart, memberName, classFile, project, isMethodCall);
                                        targets.addAll(classMembers);
                                        
                                        // 只处理第一个找到的类定义
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOG.debug("Error processing class file: " + location.filePath, e);
                    }
                }
            }
            
        } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
            // 重新抛出控制流异常，不应该记录日志
            throw e;
        } catch (Exception e) {
            LOG.warn("Error finding members in specific class", e);
        }
        
        return targets;
    }
    
    /**
     * 在类定义中查找成员
     */
    private List<PsiElement> findMembersInClass(String fileText, int classStart, String memberName, VirtualFile file, Project project) {
        return findMembersInClass(fileText, classStart, memberName, file, project, false);
    }
    
    /**
     * 在类定义中查找成员（带方法调用标识）
     */
    private List<PsiElement> findMembersInClass(String fileText, int classStart, String memberName, VirtualFile file, Project project, boolean isMethodCall) {
        List<PsiElement> members = new ArrayList<>();
        
        try {
            // 找到类的开始位置（{ 之后）
            int classBodyStart = fileText.indexOf('{', classStart);
            if (classBodyStart == -1) {
                return members;
            }
            
            // 找到类的结束位置（匹配的 }）
            int braceCount = 1;
            int classBodyEnd = classBodyStart + 1;
            while (classBodyEnd < fileText.length() && braceCount > 0) {
                char c = fileText.charAt(classBodyEnd);
                if (c == '{') {
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                }
                classBodyEnd++;
            }
            
            if (classBodyEnd > classBodyStart) {
                String classBody = fileText.substring(classBodyStart + 1, classBodyEnd - 1);
                LOG.info("GTD: searching for member '" + memberName + "' in class body (method call: " + isMethodCall + "): " + classBody.substring(0, Math.min(100, classBody.length())));
                
                if (isMethodCall) {
                    // 如果是方法调用，只查找方法定义
                    String methodPattern = "function\\s+(" + java.util.regex.Pattern.quote(memberName) + ")\\s*\\(";
                    java.util.regex.Pattern regex = java.util.regex.Pattern.compile(methodPattern);
                    java.util.regex.Matcher matcher = regex.matcher(classBody);
                    
                    while (matcher.find()) {
                        int relativeStart = matcher.start(1);
                        int absoluteStart = classBodyStart + 1 + relativeStart;
                        
                        LOG.info("GTD: found method " + memberName + "() at " + absoluteStart);
                        PsiElement member = createCrossFileNavigationElement(project, file, absoluteStart, memberName);
                        if (member != null) {
                            members.add(member);
                        }
                    }
                } else {
                    // 如果不是方法调用，只查找属性定义
                    String propertyPattern = "\\$(" + java.util.regex.Pattern.quote(memberName) + ")\\b";
                    java.util.regex.Pattern regex = java.util.regex.Pattern.compile(propertyPattern);
                    java.util.regex.Matcher matcher = regex.matcher(classBody);
                    
                    while (matcher.find()) {
                        int relativeStart = matcher.start(1);
                        int absoluteStart = classBodyStart + 1 + relativeStart;
                        
                        LOG.info("GTD: found property $" + memberName + " at " + absoluteStart);
                        PsiElement member = createCrossFileNavigationElement(project, file, absoluteStart, "$" + memberName);
                        if (member != null) {
                            members.add(member);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            LOG.debug("Error finding members in class", e);
        }
        
        return members;
    }

    /**
     * 判断是否为属性访问上下文：前面是 '->' 且后面不是 '('。
     */
    private boolean isPropertyAccessContext(Editor editor, TextRange wordRange) {
        try {
            CharSequence text = editor.getDocument().getCharsSequence();
            int start = wordRange.getStartOffset();
            int end = wordRange.getEndOffset();
            char prev1 = start - 1 >= 0 ? text.charAt(start - 1) : '\0';
            char prev2 = start - 2 >= 0 ? text.charAt(start - 2) : '\0';
            char next = end < text.length() ? text.charAt(end) : '\0';
            boolean hasArrow = (prev2 == '-' && prev1 == '>');
            boolean hasParen = (next == '(');
            return hasArrow && !hasParen;
        } catch (Throwable ignore) {
            return false;
        }
    }

    /**
     * 简单提取当前文件 namespace，用于命名空间索引优先匹配属性。
     */
    private String extractNamespace(String fileText) {
        try {
            java.util.regex.Pattern NS = java.util.regex.Pattern.compile("(?m)^\\s*namespace\\s+([A-Za-z_\\\\][A-Za-z0-9_\\\\]*)");
            java.util.regex.Matcher m = NS.matcher(fileText);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 从命名空间 JSON 索引中查找属性定义，优先返回 kind=property 的命中。
     */
    private List<PsiElement> findPropertyFromNamespaceIndex(Project project, String namespace, String classShortName, String memberName, VirtualFile currentFile, int skipStart, int skipEnd) {
        List<PsiElement> targets = new ArrayList<>();
        try {
            if (namespace == null || namespace.isEmpty()) return targets;
            com.company.plugin.index.ZyJsonIndexStore.DirIndex idx = com.company.plugin.index.ZyJsonIndexStore.readNamespaceIndex(project, namespace);
            if (idx == null || idx.files == null) return targets;
            String basePath = project.getBasePath();
            String propName = "$" + memberName;
            for (com.company.plugin.index.ZyJsonIndexStore.FileEntry f : idx.files) {
                if (f.symbols == null || f.symbols.isEmpty()) continue;
                for (com.company.plugin.index.ZyJsonIndexStore.SymbolEntry s : f.symbols) {
                    if (!"property".equals(s.kind)) continue;
                    if (!propName.equals(s.name)) continue;
                    // 若提供了类短名，则要求 fqn 以 Namespace\Class::$prop 形式匹配对应类
                    if (classShortName != null && !classShortName.isEmpty()) {
                        if (s.fqn == null || !s.fqn.endsWith("\\" + classShortName + "::" + propName)) {
                            continue;
                        }
                    }
                    String path = (basePath != null ? basePath + "/" : "") + f.path;
                    VirtualFile vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(path);
                    if (vf == null) continue;
                    if (vf.equals(currentFile) && s.offset >= skipStart && s.offset < skipEnd) continue;
                    PsiElement target = createCrossFileNavigationElement(project, vf, s.offset, propName);
                    if (target != null) targets.add(target);
                }
            }
        } catch (Throwable t) {
            LOG.debug("Namespace property index lookup failed", t);
        }
        return targets;
    }

    /**
     * 从文本向左回溯获取 '$var->ClassOrFactory()->...->' 链的紧邻类短名，简单启发式：
     * - 匹配 "new ClassName" 或 "use Alias as ClassName" 后的短名；
     * - 退化：返回空表示不限制类名。
     */
    private String extractClassNameForPropertyChain(CharSequence text, int nameStart) {
        try {
            int i = nameStart - 1;
            // 回溯找到 '->'
            while (i >= 1) {
                if (text.charAt(i) == '>' && text.charAt(i - 1) == '-') { break; }
                i--;
            }
            // 再继续向左收集一个可能的标识符或 new 片段
            int j = i - 1;
            while (j >= 0 && Character.isWhitespace(text.charAt(j))) j--;
            // 检查是否存在 "new ClassName" 模式
            int end = j;
            while (j >= 0 && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '_')) j--;
            String token = end >= 0 ? text.subSequence(j + 1, end + 1).toString() : null;
            if (token != null && !token.isEmpty()) {
                // 简单返回该标识符作为类短名候选
                return token;
            }
        } catch (Throwable ignore) {}
        return null;
    }
    
    /**
     * 解析 use 语句，获取真实的类名
     */
    private String parseUseStatementsAndGetRealClassName(String fileText, String word) {
        try {
            // 解析 use 语句，返回短名->命名空间全名映射
            java.util.Map<String, String> useMap = parseUseStatements(fileText);
            String fqn = useMap.get(word);
            if (fqn != null && !fqn.isEmpty()) {
                // 如果有别名映射，返回完全限定名
                return fqn;
            }
        } catch (Exception e) {
            LOG.debug("Error parsing use statements", e);
        }
        
        // 如果没有找到映射，返回原始单词
        return word;
    }
    
    /**
     * 解析 use 语句，返回短名->命名空间全名映射
     */
    private static java.util.Map<String, String> parseUseStatements(String fileText) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        try {
            // 改进的正则表达式，支持多行和更灵活的格式
            // 匹配: use Namespace\Class; 或 use Namespace\Class as Alias;
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?m)^\\s*use\\s+([\\\\\\w\\\\]+)(?:\\s+as\\s+(\\w+))?\\s*;");
            java.util.regex.Matcher matcher = pattern.matcher(fileText);
            while (matcher.find()) {
                String fqn = matcher.group(1);
                String alias = matcher.group(2);
                
                LOG.debug("GTD: parsed use statement - FQN: '" + fqn + "', alias: '" + alias + "'");
                
                // 如果有别名，使用别名作为键
                if (alias != null && !alias.isEmpty()) {
                    map.put(alias, fqn);
                    LOG.debug("GTD: mapped alias '" + alias + "' to FQN '" + fqn + "'");
                } else {
                    // 否则使用完全限定名的最后一部分作为键
                    String[] parts = fqn.split("\\\\");
                    if (parts.length > 0) {
                        String shortName = parts[parts.length - 1];
                        map.put(shortName, fqn);
                        LOG.debug("GTD: mapped short name '" + shortName + "' to FQN '" + fqn + "'");
                    }
                }
            }
            
            LOG.debug("GTD: parsed " + map.size() + " use statements: " + map);
        } catch (Exception e) {
            LOG.debug("Error parsing use statements", e);
        }
        return map;
    }
}
