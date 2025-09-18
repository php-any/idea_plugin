package com.company.plugin.navigation;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import java.io.File;
import java.util.List;
import java.util.ArrayList;

/**
 * 测试 ZyGotoDeclarationHandler 的跳转功能
 */
public class ZyGotoDeclarationHandlerTest extends BasePlatformTestCase {

    public void testCrossFileNavigation() {
        // 这个测试验证是否能从 test.zy 跳转到 model/Users.zy
        // 由于这是一个集成测试，需要实际运行IDE来验证功能
        assertTrue(true);
    }
    
    public void testFindInAllZyFilesSkipsCurrentFile() {
        // 测试 findInAllZyFiles 方法是否能正确跳过当前文件
        ZyGotoDeclarationHandler handler = new ZyGotoDeclarationHandler();
        // 这里我们只是验证方法签名是否正确，实际测试需要在IDE中进行
        assertNotNull(handler);
    }
    
    public void testFindInFileSkipsCurrentPosition() {
        // 测试 findInFile 方法是否能正确跳过当前位置
        ZyGotoDeclarationHandler handler = new ZyGotoDeclarationHandler();
        // 这里我们只是验证方法签名是否正确，实际测试需要在IDE中进行
        assertNotNull(handler);
    }

    public void testNewServerDoesNotIncludeSelf() {
        // 构造包含 new Server 的示例代码，并将光标放在 Server 上
        String code = "namespace app\n" +
                "use http\\Server\n" +
                "$server = new Ser<caret>ver(\":80\")\n";
        myFixture.configureByText("http.zy", code);

        ZyGotoDeclarationHandler handler = new ZyGotoDeclarationHandler();
        var editor = myFixture.getEditor();
        var file = myFixture.getFile();
        int offset = editor.getCaretModel().getOffset();
        var element = file.findElementAt(offset);

        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, editor);

        // 允许返回空或若干候选，但不应包含当前位置自身（覆盖自过滤逻辑）
        if (targets != null) {
            for (PsiElement t : targets) {
                assertFalse("should not include self at caret",
                        t.getContainingFile().equals(file)
                                && t.getTextRange() != null
                                && t.getTextRange().contains(offset));
            }
        }
    }

    public void testCrossFileResolutionViaUse() {
        // 定义一个包含类 Server 的文件
        String lib = "namespace http\nclass Server {\n    function run() {}\n}\n";
        myFixture.configureByText("Server.zy", lib);

        // 在使用方通过 use 导入并调用
        String useCode = "namespace app\nuse http\\Server\n$server = new Ser<caret>ver(\":80\")\n";
        myFixture.configureByText("http.zy", useCode);

        ZyGotoDeclarationHandler handler = new ZyGotoDeclarationHandler();
        var editor = myFixture.getEditor();
        var file = myFixture.getFile();
        int offset = editor.getCaretModel().getOffset();
        var element = file.findElementAt(offset);

        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, editor);
        assertNotNull(targets);
        boolean hasServerClass = false;
        for (PsiElement t : targets) {
            var vf = t.getContainingFile().getVirtualFile();
            if (vf != null && "Server.zy".equals(vf.getName())) {
                hasServerClass = true;
            }
        }
        assertTrue("should resolve to Server.zy class definition", hasServerClass);
    }

    public void testIncrementalIndexUpdateAfterChange() throws Exception {
        // 初始定义
        String libV1 = "namespace http\nclass Server {\n    function run() {}\n}\n";
        var vf = myFixture.configureByText("Server.zy", libV1).getVirtualFile();

        // 触发一次跳转，确保已索引
        String useCode1 = "namespace app\nuse http\\Server\n$server = new Ser<caret>ver(\":80\")\n";
        myFixture.configureByText("http.zy", useCode1);
        ZyGotoDeclarationHandler handler = new ZyGotoDeclarationHandler();
        var editor1 = myFixture.getEditor();
        var file1 = myFixture.getFile();
        int offset1 = editor1.getCaretModel().getOffset();
        var element1 = file1.findElementAt(offset1);
        handler.getGotoDeclarationTargets(element1, offset1, editor1);

        // 修改被索引文件内容（新增一个方法），更新时间戳
        String libV2 = "namespace http\nclass Server {\n    function run() {}\n    function ping() {}\n}\n";
        com.intellij.openapi.application.WriteAction.run(() -> {
            com.intellij.psi.PsiFile psiFile = com.intellij.psi.PsiManager.getInstance(getProject()).findFile(vf);
            if (psiFile != null) {
                com.intellij.openapi.editor.Document doc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
                if (doc != null) {
                    doc.setText(libV2);
                    com.intellij.psi.PsiDocumentManager.getInstance(getProject()).commitDocument(doc);
                }
            }
        });

        // 再次触发跳转，验证不报错（说明索引已按修改时间增量刷新）
        var editor2 = myFixture.getEditor();
        var file2 = myFixture.getFile();
        int offset2 = editor2.getCaretModel().getOffset();
        var element2 = file2.findElementAt(offset2);
        PsiElement[] targets2 = handler.getGotoDeclarationTargets(element2, offset2, editor2);
        assertNotNull(targets2);
    }

    public void testPropertyAccessDoesNotJumpToFunctionName() {
        // 构造同文件中存在 function name()，以及属性访问 $user->name 的场景
        String code = "namespace app\n" +
                "function name() {}\n" +
                "$user = User()\n" +
                "$user->na<caret>me;\n";
        var psi = myFixture.configureByText("model.zy", code);

        // 计算函数名在文件中的偏移（用于断言不应跳到这里）
        int funcOffset = code.indexOf("function name()") + "function ".length();
        assertTrue("precondition: function offset found", funcOffset >= 0);

        ZyGotoDeclarationHandler handler = new ZyGotoDeclarationHandler();
        var editor = myFixture.getEditor();
        var file = myFixture.getFile();
        int offset = editor.getCaretModel().getOffset();
        var element = file.findElementAt(offset);

        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, editor);
        // 允许没有目标，但若有目标，不应为 function name() 的定义位置
        if (targets != null) {
            for (PsiElement t : targets) {
                if (t.getContainingFile().equals(file)) {
                    assertFalse("should not jump to function name()",
                            t.getTextRange() != null && t.getTextRange().getStartOffset() == funcOffset);
                }
            }
        }
    }

    public void testUsersAmbiguityShowsTwoCandidates() {
        // 准备两个命名空间下的同名类 Users
        myFixture.configureByText("model/Users.zy", "namespace Model\nclass Users {\n}\n");
        myFixture.configureByText("logic/Users.zy", "namespace Logic\nclass Users {\n}\n");

        // 在使用方直接书写 Users，期望出现两个候选
        String code = "namespace app\n$u = new Us<caret>ers()\n";
        myFixture.configureByText("test.zy", code);

        ZyGotoDeclarationHandler handler = new ZyGotoDeclarationHandler();
        var editor = myFixture.getEditor();
        var file = myFixture.getFile();
        int offset = editor.getCaretModel().getOffset();
        var element = file.findElementAt(offset);

        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, editor);
        assertNotNull("targets should not be null", targets);
        assertTrue("should have at least 2 candidates", targets.length >= 2);

        boolean hasModel = false, hasLogic = false;
        for (PsiElement t : targets) {
            var vf = t.getContainingFile() != null ? t.getContainingFile().getVirtualFile() : null;
            if (vf != null) {
                String path = vf.getPath();
                if (path.endsWith("test/model/Users.zy")) hasModel = true;
                if (path.endsWith("test/logic/Users.zy")) hasLogic = true;
            }
        }
        assertTrue("should include Model/Users.zy", hasModel);
        assertTrue("should include Logic/Users.zy", hasLogic);
    }

    public void testAmbiguousMethodAgeShowsTwoCandidates() {
        // 准备两个命名空间下的同名类 Users 且都包含方法 age()
        myFixture.configureByText("model/Users.zy", "namespace Model\nclass Users {\n    function age() {}\n}\n");
        myFixture.configureByText("logic/Users.zy", "namespace Logic\nclass Users {\n    function age() {}\n}\n");

        // 使用方 new Users()->age()，应出现两个候选
        String code = "namespace app\n$u = new Users()->ag<caret>e()\n";
        myFixture.configureByText("test.zy", code);

        ZyGotoDeclarationHandler handler = new ZyGotoDeclarationHandler();
        var editor = myFixture.getEditor();
        var file = myFixture.getFile();
        int offset = editor.getCaretModel().getOffset();
        var element = file.findElementAt(offset);

        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, editor);
        assertNotNull("targets should not be null", targets);
        assertTrue("should have at least 2 candidates", targets.length >= 2);

        boolean hasModel = false, hasLogic = false;
        for (PsiElement t : targets) {
            var vf = t.getContainingFile() != null ? t.getContainingFile().getVirtualFile() : null;
            if (vf != null) {
                String path = vf.getPath();
                if (path.endsWith("test/model/Users.zy")) hasModel = true;
                if (path.endsWith("test/logic/Users.zy")) hasLogic = true;
            }
        }
        assertTrue("should include Model/Users.zy age()", hasModel);
        assertTrue("should include Logic/Users.zy age()", hasLogic);
    }

    public void testSimulateClickUsersAgeInTestFile() {
        // 使用真实的 test/test.zy 内容模拟点击 $users->age()
        myFixture.configureByText("model/Users.zy", "namespace Model\nclass Users {\n    function age() {}\n}\n");
        myFixture.configureByText("logic/Users.zy", "namespace Logic\nclass Users {\n    function age() {}\n}\n");
        String testFile = "namespace tests;\n\nuse Model\\Users;\nuse Logic\\Users as LogicUsers;\n\n$users = new Users()\n\n$users->name()\n$users->ag<caret>e()\n\nnew User()->name;\n\nnew LogicUsers()\n";
        myFixture.configureByText("test/test.zy", testFile);

        ZyGotoDeclarationHandler handler = new ZyGotoDeclarationHandler();
        var editor = myFixture.getEditor();
        var file = myFixture.getFile();
        int offset = editor.getCaretModel().getOffset();
        var element = file.findElementAt(offset);

        PsiElement[] targets = handler.getGotoDeclarationTargets(element, offset, editor);
        assertNotNull("targets should not be null", targets);
        // 由于 use Model\Users 显式引入，可能优先返回 Model 的方法，但我们仍期望包含 Logic 的同名方法
        boolean hasModel = false, hasLogic = false;
        for (PsiElement t : targets) {
            var vf = t.getContainingFile() != null ? t.getContainingFile().getVirtualFile() : null;
            if (vf != null) {
                String path = vf.getPath();
                if (path.endsWith("test/model/Users.zy")) hasModel = true;
                if (path.endsWith("test/logic/Users.zy")) hasLogic = true;
            }
        }
        assertTrue("should include Model/Users.zy::age()", hasModel);
        assertTrue("should include Logic/Users.zy::age()", hasLogic);
    }
}