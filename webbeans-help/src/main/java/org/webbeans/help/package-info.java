/**
 * The help system: {@link org.webbeans.help.HelpPage} registrations, the Help window, F1, the Help buttons of
 * dialogs and the stand-alone page at {@code /wb/help/<id>}. Ships one page about the window system itself.
 */
@HelpPage(id = "platform", title = "#HLP_Platform", resource = "org/webbeans/help/platform.md", position = 9000,
        keywords = "#KW_Platform")
@Messages({"HLP_Platform=Getting around", "KW_Platform=windows, dock, layout, shortcuts, options, help"})
package org.webbeans.help;

import org.webbeans.core.i18n.Messages;
