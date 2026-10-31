/**
 * API del módulo cobranza (objetos de transferencia): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("cobro-dto")
package com.bomerp.cobranza.cobro.dto;

import org.springframework.modulith.NamedInterface;
