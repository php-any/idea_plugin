package com.company.plugin.navigation

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.intellij.openapi.util.TextRange

/**
 * ZY 引用贡献器
 * 基于纯文本 PSI，为 .zy 文件中的标识符（[A-Za-z0-9_]+）提供精确的引用范围
 * 目的：让 Alt 悬停下划线仅覆盖标识符范围，而不是整文件
 */
class ZyReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        // 在 ZY 语言文件的纯文本元素上提供引用
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiElement::class.java).inFile(PlatformPatterns.psiFile()),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    val file = element.containingFile?.virtualFile ?: return PsiReference.EMPTY_ARRAY
                    if (!file.name.endsWith(".zy")) return PsiReference.EMPTY_ARRAY

                    val text = element.text ?: return PsiReference.EMPTY_ARRAY
                    if (text.isBlank()) return PsiReference.EMPTY_ARRAY

                    // 按行切分，避免跨行引用导致整段文本被处理
                    val references = mutableListOf<PsiReference>()
                    var base = 0
                    val lines = text.split('\n')
                    for (line in lines) {
                        var i = 0
                        while (i < line.length) {
                            while (i < line.length && !isWordChar(line[i])) i++
                            val start = i
                            while (i < line.length && isWordChar(line[i])) i++
                            val end = i
                            if (end > start) {
                                val range = TextRange(base + start, base + end)
                                references.add(ZyWordReference(element, range))
                            }
                        }
                        base += line.length + 1
                    }

                    return references.toTypedArray()
                }
            }
        )
    }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
}

/**
 * ZY 标识符引用
 * 仅用于提供下划线的精确范围，实际解析跳转交由 GotoDeclarationHandler + LSP 处理
 */
class ZyWordReference(
    element: PsiElement,
    private val rangeInElement: TextRange
) : PsiReferenceBase<PsiElement>(element, rangeInElement, false) {

    override fun resolve(): PsiElement? {
        // 解析交由 LSP，引用本身不直接解析
        return null
    }

    override fun getVariants(): Array<Any> = emptyArray()

    override fun getRangeInElement(): TextRange = rangeInElement
}


