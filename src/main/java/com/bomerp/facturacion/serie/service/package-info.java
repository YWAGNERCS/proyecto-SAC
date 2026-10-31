/**
 * API del módulo facturacion (servicios (fachadas)): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("serie-service")
package com.bomerp.facturacion.serie.service;

import org.springframework.modulith.NamedInterface;
