/**
 * API del módulo config (pieza compartida): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("exportacion")
package com.bomerp.config.exportacion;

import org.springframework.modulith.NamedInterface;
