/**
 * API del módulo reportes (servicios (fachadas)): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("reportes-service")
package com.bomerp.reportes.reportes.service;

import org.springframework.modulith.NamedInterface;
