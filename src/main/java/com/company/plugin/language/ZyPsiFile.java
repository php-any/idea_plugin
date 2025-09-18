package com.company.plugin.language;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

/**
 * ZY PSI 文件封装
 */
public class ZyPsiFile extends PsiFileBase {
    
    public ZyPsiFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, ZyLanguage.INSTANCE);
    }
    
    @Override
    @NotNull
    public FileType getFileType() {
        return ZyFileType.INSTANCE;
    }
    
    @Override
    @NotNull
    public String toString() {
        return "ZY File";
    }
}
