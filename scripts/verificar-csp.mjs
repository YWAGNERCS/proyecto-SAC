// Compara las huellas (sha256) de los scripts en línea del index.html compilado con las que permite la
// Content-Security-Policy de nginx (cabeceras-seguridad.conf). Corre después del build de producción:
//   npm run build:prod
// Si un script en línea cambió (por ejemplo, al editar el del tema o al actualizar Angular), el navegador
// lo bloquearía en producción. Aquí se detecta antes: termina con error y muestra la huella correcta.
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';

const indice = readFileSync('dist/bomerp-frontend/browser/index.html', 'utf8');
const cabeceras = readFileSync('cabeceras-seguridad.conf', 'utf8');

const csp = cabeceras.match(/Content-Security-Policy "([^"]+)"/)?.[1];
if (!csp) {
  console.error('verificar-csp: no encuentro la Content-Security-Policy en cabeceras-seguridad.conf');
  process.exit(1);
}
const scriptSrc = csp.split(';').map((d) => d.trim()).find((d) => d.startsWith('script-src')) ?? '';
const permitidas = new Set(scriptSrc.match(/'sha256-[^']+'/g)?.map((h) => h.slice(1, -1)) ?? []);

const enLinea = [...indice.matchAll(/<script>([\s\S]*?)<\/script>/g)].map((m) => m[1]);
const huellas = enLinea.map((s) => 'sha256-' + createHash('sha256').update(s, 'utf8').digest('base64'));

const faltan = huellas.filter((h) => !permitidas.has(h));
const sobran = [...permitidas].filter((h) => !huellas.includes(h));

if (faltan.length) {
  console.error(`verificar-csp: ${faltan.length} script(s) en línea del index.html no están permitidos por la CSP.`);
  console.error('El navegador los bloquearía. En cabeceras-seguridad.conf, script-src debe llevar:');
  console.error("  script-src 'self' " + huellas.map((h) => `'${h}'`).join(' ') + ';');
  process.exit(1);
}
if (sobran.length) {
  console.warn(`verificar-csp: aviso, la CSP permite ${sobran.length} huella(s) que ya no se usan: ${sobran.join(' ')}`);
}
console.log(`verificar-csp: OK, los ${huellas.length} scripts en línea del index.html están permitidos por la CSP.`);
