/**
 * API del módulo seguridad (servicios (fachadas)): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("auditoria-service")
package com.bomerp.seguridad.auditoria.service;

import org.springframework.modulith.NamedInterface;
