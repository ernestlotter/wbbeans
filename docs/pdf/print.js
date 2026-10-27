const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage();
  await p.goto('file://' + __dirname + '/guide.html', { waitUntil: 'load' });
  await p.emulateMedia({ media: 'print' });
  await p.pdf({ path: __dirname + '/WebBeans-User-Guide.pdf', format: 'A4', printBackground: true,
    displayHeaderFooter: true,
    headerTemplate: '<div style="font-size:7.5pt;color:#6b7280;width:100%;padding:0 16mm;display:flex;justify-content:space-between"><span>WebBeans User\'s Guide for Developers</span><span>webbeans / docs/user-guide.md</span></div>',
    footerTemplate: '<div style="font-size:7.5pt;color:#6b7280;width:100%;text-align:center">Page <span class="pageNumber"></span> of <span class="totalPages"></span></div>',
    margin: { top: '18mm', bottom: '18mm', left: '16mm', right: '16mm' } });
  await b.close();
})();
