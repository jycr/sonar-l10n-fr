// Prend une capture d'écran de preuve. Lancé par capture.sh dans l'image Playwright.
//
//   node capture.mjs <url> <sortie.png> [options]
//     --sonar             se connecter d'abord à SonarQube (SQ_USER / SQ_PASSWORD, défaut admin/admin)
//     --cliquer "..."     clique d'abord sur ce texte, s'il est présent (fermer un bandeau ; répétable)
//     --texte "..."       encadre en rouge le premier élément qui contient ce texte (répétable)
//     --selecteur "css"   ne capture que cet élément
//     --pleine-page       capture toute la hauteur de la page
//     --attendre <ms>     délai après chargement (défaut 1500)
//     --largeur <px>      largeur de la fenêtre (défaut 1440)
import { chromium } from 'playwright';

const [url, out, ...rest] = process.argv.slice(2);
if (!url || !out) {
  console.error('usage : capture.mjs <url> <sortie.png> [--sonar] [--texte ...] [--selecteur css] [--pleine-page]');
  process.exit(2);
}
const opts = { textes: [], clics: [], attendre: 1500, largeur: 1440 };
for (let i = 0; i < rest.length; i++) {
  switch (rest[i]) {
    case '--sonar': opts.sonar = true; break;
    case '--cliquer': opts.clics.push(rest[++i]); break;
    case '--texte': opts.textes.push(rest[++i]); break;
    case '--selecteur': opts.selecteur = rest[++i]; break;
    case '--pleine-page': opts.pleinePage = true; break;
    case '--attendre': opts.attendre = Number(rest[++i]); break;
    case '--largeur': opts.largeur = Number(rest[++i]); break;
    default: console.error(`option inconnue : ${rest[i]}`); process.exit(2);
  }
}

const browser = await chromium.launch();
// fr-FR : SonarQube choisit sa langue d'après Accept-Language, c'est ce qui active le pack.
const context = await browser.newContext({
  locale: 'fr-FR',
  timezoneId: 'Europe/Paris',
  viewport: { width: opts.largeur, height: 900 },
  deviceScaleFactor: 1,
});
const page = await context.newPage();
try {
  if (opts.sonar) {
    const base = new URL(url).origin;
    const login = await page.request.post(`${base}/api/authentication/login`, {
      form: { login: process.env.SQ_USER || 'admin', password: process.env.SQ_PASSWORD || 'admin' },
    });
    if (!login.ok()) throw new Error(`connexion SonarQube refusée (${login.status()})`);
  }
  await page.goto(url, { waitUntil: 'networkidle', timeout: 60000 });
  // Écrans que SonarQube impose une fois par instance, avant toute autre page.
  for (const [path, why] of [
    ['/account/reset_password', 'changer le mot de passe admin par défaut, puis passer SQ_PASSWORD'],
    ['/admin/plugin_risk_consent', 'accepter le risque lié aux plugins tiers'],
  ]) {
    if (page.url().includes(path)) {
      throw new Error(`SonarQube redirige vers ${path} : ${why} (voir « Préparer la recette » dans SKILL.md)`);
    }
  }
  await page.waitForTimeout(opts.attendre);
  for (const texte of opts.clics) {
    const el = page.getByText(texte, { exact: true }).first();
    if (await el.isVisible().catch(() => false)) {
      await el.click();
      await page.waitForTimeout(500);
    }
  }
  for (const texte of opts.textes) {
    const el = page.getByText(texte, { exact: false }).first();
    await el.waitFor({ timeout: 15000 });
    await el.scrollIntoViewIfNeeded();
    await el.evaluate((node) => {
      node.style.outline = '3px solid #d32f2f';
      node.style.outlineOffset = '3px';
    });
  }
  if (opts.selecteur) {
    await page.locator(opts.selecteur).first().screenshot({ path: out });
  } else {
    await page.screenshot({ path: out, fullPage: !!opts.pleinePage });
  }
  console.log(`capture : ${out}`);
} finally {
  await browser.close();
}
