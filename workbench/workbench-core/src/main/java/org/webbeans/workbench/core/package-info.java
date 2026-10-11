/** Workbench core: the Output window, status cells, options, accounts, About, Log out — and the help home page. */
@HelpPage(id = "workbench", title = "#HLP_Workbench", resource = "org/webbeans/workbench/core/help/workbench.md", position = 0,
        keywords = "#KW_Workbench")
@HelpPage(id = "workbench.output", parent = "workbench", title = "#HLP_Output", resource = "org/webbeans/workbench/core/help/output.md", position = 900)
@HelpPage(id = "workbench.options", parent = "workbench", title = "#HLP_Options", resource = "org/webbeans/workbench/core/help/options.md", position = 950)
@Messages({"HLP_Workbench=Welcome to the Workbench", "KW_Workbench=overview, first steps, accounts, alice, bob", "HLP_Output=Output", "HLP_Options=Workbench options"})
package org.webbeans.workbench.core;

import org.webbeans.core.i18n.Messages;
import org.webbeans.help.HelpPage;
