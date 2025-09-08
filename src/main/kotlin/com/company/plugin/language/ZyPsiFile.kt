package com.company.plugin.language

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

/**
 * ZY PSI 文件封装
 */
class ZyPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, ZyLanguage) {
    override fun getFileType(): FileType = ZyFileType.INSTANCE
    override fun toString(): String = "ZY File"
}


