/**
 * API del módulo config (pieza compartida): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("empresa")
package com.bomerp.config.empresa;

import org.springframework.modulith.NamedInterface;
