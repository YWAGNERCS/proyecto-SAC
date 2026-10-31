/**
 * API del módulo inventario (servicios (fachadas)): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("ajuste-service")
package com.bomerp.inventario.ajuste.service;

import org.springframework.modulith.NamedInterface;
