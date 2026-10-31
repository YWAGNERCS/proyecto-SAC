/**
 * API del módulo proformas (servicios (fachadas)): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("proforma-service")
package com.bomerp.proformas.proforma.service;

import org.springframework.modulith.NamedInterface;
