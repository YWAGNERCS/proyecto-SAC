/**
 * API del módulo cobranza (servicios (fachadas)): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("cobro-service")
package com.bomerp.cobranza.cobro.service;

import org.springframework.modulith.NamedInterface;
