/**
 * PKCE (RFC 7636) con la Web Crypto API del navegador, sin librerías.
 * El code_verifier es secreto y se queda en la SPA; a Keycloak solo viaja su hash (code_challenge).
 * Al canjear el código, Keycloak exige el verifier original: un código robado no sirve sin él.
 */
function base64UrlEncode(bytes: Uint8Array): string {
  let binario = '';
  bytes.forEach((byte) => (binario += String.fromCharCode(byte)));
  return btoa(binario).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** 32 bytes aleatorios (256 bits): la entropía mínima que pide RFC 7636. */
export function generarCodeVerifier(): string {
  return base64UrlEncode(crypto.getRandomValues(new Uint8Array(32)));
}

/** SHA-256 del verifier en base64url (método S256, el único que acepta el cliente). */
export async function generarCodeChallenge(verifier: string): Promise<string> {
  const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return base64UrlEncode(new Uint8Array(hash));
}

/** Valor impredecible que se compara al volver de Keycloak (protección CSRF del login). */
export function generarState(): string {
  return base64UrlEncode(crypto.getRandomValues(new Uint8Array(16)));
}
