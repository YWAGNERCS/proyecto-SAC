/**
 * API del módulo servicios (servicios (fachadas)): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("orden-service")
package com.bomerp.servicios.orden.service;

import org.springframework.modulith.NamedInterface;
