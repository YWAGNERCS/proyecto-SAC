/**
 * API del módulo postventa (servicios (fachadas)): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("equipo-service")
package com.bomerp.postventa.equipo.service;

import org.springframework.modulith.NamedInterface;
