package com.company.plugin.navigation

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project

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
        // 根据名称返回导航项
        // 目前返回空数组，待完善
        return emptyArray()
    }
}
