package org.webbeans.help;

import org.webbeans.windows.TopComponent;

/** Windows and pages of the help tests. */
@HelpPage(id = "test.root", title = "Test root", resource = "org/webbeans/help/test/root.md", position = 10, keywords = "alpha, beta")
@HelpPage(id = "test.child", title = "Child page", resource = "org/webbeans/help/test/child.md", parent = "test.root", position = 10)
@HelpPage(id = "test.other", title = "Other", resource = "org/webbeans/help/test/missing.md", position = 20)
@HelpPage(id = "test.secret", title = "Admin only", resource = "org/webbeans/help/test/child.md", position = 30, roles = "admin")
public final class TestWindows {

    private TestWindows() {
    }

    @TopComponent.Registration(mode = "editor", openAtStartup = false)
    @TopComponent.Description(preferredID = "HelpTestTC", helpId = "test.child")
    public static final class WithHelp extends TopComponent {
        public WithHelp() {
            setDisplayName("With help");
        }
    }

    @TopComponent.Registration(mode = "editor", openAtStartup = false)
    @TopComponent.Description(preferredID = "HelpTestPlainTC")
    public static final class WithoutHelp extends TopComponent {
        public WithoutHelp() {
            setDisplayName("Without help");
        }
    }
}
