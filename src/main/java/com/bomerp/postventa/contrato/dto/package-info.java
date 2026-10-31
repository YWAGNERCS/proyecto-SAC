/**
 * API del módulo postventa (objetos de transferencia): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("contrato-dto")
package com.bomerp.postventa.contrato.dto;

import org.springframework.modulith.NamedInterface;
