/** Workbench files: the explorer, the editor and the file actions — and their help pages. */
@HelpPage(id = "files", parent = "workbench", title = "#HLP_Files", resource = "org/webbeans/workbench/files/help/files.md", position = 100,
        keywords = "#KW_Files")
@HelpPage(id = "files.editor", parent = "files", title = "#HLP_Editor", resource = "org/webbeans/workbench/files/help/editor.md", position = 100,
        keywords = "#KW_Editor")
@Messages({"HLP_Files=Files", "KW_Files=explorer, folder, rename, delete, refresh, new file", "HLP_Editor=The editor", "KW_Editor=save, unsaved changes, text"})
package org.webbeans.workbench.files;

import org.webbeans.core.i18n.Messages;
import org.webbeans.help.HelpPage;
