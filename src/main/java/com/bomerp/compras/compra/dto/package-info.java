/**
 * API del módulo compras (objetos de transferencia): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("compra-dto")
package com.bomerp.compras.compra.dto;

import org.springframework.modulith.NamedInterface;
