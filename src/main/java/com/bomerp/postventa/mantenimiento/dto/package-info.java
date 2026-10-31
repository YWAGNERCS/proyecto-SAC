/**
 * API del módulo postventa (objetos de transferencia): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("mantenimiento-dto")
package com.bomerp.postventa.mantenimiento.dto;

import org.springframework.modulith.NamedInterface;
