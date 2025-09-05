package com.company.plugin.navigation

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.company.plugin.lsp.ZyLspService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.application.ApplicationManager

/**
 * ZY 导航提供者
 * 提供基本的代码导航功能
 */
class ZyNavigationProvider : ChooseByNameContributor {
    
    override fun getNames(project: Project, includeNonProjectItems: Boolean): Array<String> {
        // 返回项目中的 ZY 符号名称
        // 目前返回空数组，待完善
        return emptyArray()
    }
    
    override fun getItemsByName(
        name: String,
        pattern: String,
        project: Project,
        includeNonProjectItems: Boolean
    ): Array<NavigationItem> {
        // 暂不实现符号列表检索
        return emptyArray()
    }
}
