/**
 * API del módulo compras (servicios (fachadas)): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("compra-service")
package com.bomerp.compras.compra.service;

import org.springframework.modulith.NamedInterface;
