/**
 * API del módulo proformas (objetos de transferencia): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("proforma-dto")
package com.bomerp.proformas.proforma.dto;

import org.springframework.modulith.NamedInterface;
