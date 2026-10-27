// node print.js <guide.html> <out.pdf> "<header title>" "<header right>"
const { chromium } = require('playwright');
const path = require('path');
const [html, out, title, right] = process.argv.slice(2);
const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage();
  await p.goto('file://' + path.resolve(html), { waitUntil: 'load' });
  await p.emulateMedia({ media: 'print' });
  await p.pdf({ path: out, format: 'A4', printBackground: true,
    displayHeaderFooter: true,
    headerTemplate: '<div style="font-size:7.5pt;color:#6b7280;width:100%;padding:0 16mm;display:flex;justify-content:space-between"><span>' + esc(title) + '</span><span>' + esc(right) + '</span></div>',
    footerTemplate: '<div style="font-size:7.5pt;color:#6b7280;width:100%;text-align:center">Page <span class="pageNumber"></span> of <span class="totalPages"></span></div>',
    margin: { top: '18mm', bottom: '18mm', left: '16mm', right: '16mm' } });
  await b.close();
})();
