package com.bomerp.facturacion.sunat.integration;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/**
 * Certificado digital con el que se firman los comprobantes (.p12 / .pfx, como el Certificado
 * Digital Tributario que entrega SUNAT). xbuilder solo lee JKS, por eso se carga aquí.
 */
record CertificadoSunat(PrivateKey clavePrivada, X509Certificate certificado) {

    static CertificadoSunat cargar(String ruta, String clave) {
        if (ruta == null || ruta.isBlank() || !Files.exists(Path.of(ruta))) {
            throw new IllegalStateException("No se encontró el certificado digital (app.sunat.certificado-ruta): " + ruta);
        }
        try (InputStream in = Files.newInputStream(Path.of(ruta))) {
            KeyStore almacen = KeyStore.getInstance("PKCS12");
            almacen.load(in, clave.toCharArray());
            for (String alias : Collections.list(almacen.aliases())) {
                if (almacen.isKeyEntry(alias)) {
                    PrivateKey privada = (PrivateKey) almacen.getKey(alias, clave.toCharArray());
                    return new CertificadoSunat(privada, (X509Certificate) almacen.getCertificate(alias));
                }
            }
            throw new IllegalStateException("El certificado " + ruta + " no tiene clave privada.");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo abrir el certificado digital: " + e.getMessage(), e);
        }
    }
}
