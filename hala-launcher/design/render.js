// Render an SVG or HTML file to PNG with Chromium (Playwright).
// Usage: node design/render.js <input> <output.png> <width> <height>
const path = require('path');
let chromium;
try {
  ({ chromium } = require('playwright'));
} catch (e) {
  ({ chromium } = require('/opt/node-tools/node_modules/playwright'));
}
(async () => {
  const [, , input, output, w, h] = process.argv;
  const browser = await chromium.launch({ args: ['--allow-file-access-from-files'] });
  const page = await browser.newPage({ viewport: { width: +w, height: +h } });
  await page.goto('file://' + path.resolve(input));
  await page.waitForTimeout(400);
  await page.screenshot({ path: output });
  await browser.close();
})();
