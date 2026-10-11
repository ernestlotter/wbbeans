/** Workbench dashboard: live charts, their options and the export action — and their help pages. */
@HelpPage(id = "dashboard", parent = "workbench", title = "#HLP_Dashboard", resource = "org/webbeans/workbench/dashboard/help/dashboard.md", position = 200,
        keywords = "#KW_Dashboard")
@HelpPage(id = "dashboard.options", parent = "dashboard", title = "#HLP_DashboardOptions", resource = "org/webbeans/workbench/dashboard/help/dashboard-options.md",
        position = 100)
@Messages({"HLP_Dashboard=Dashboard", "KW_Dashboard=charts, cpu, requests, traffic, export, png, notification", "HLP_DashboardOptions=Dashboard options"})
package org.webbeans.workbench.dashboard;

import org.webbeans.core.i18n.Messages;
import org.webbeans.help.HelpPage;
