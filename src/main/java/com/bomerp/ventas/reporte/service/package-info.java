/**
 * API del módulo ventas (servicios (fachadas)): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("reporte-service")
package com.bomerp.ventas.reporte.service;

import org.springframework.modulith.NamedInterface;
