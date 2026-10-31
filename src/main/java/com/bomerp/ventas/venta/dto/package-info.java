/**
 * API del módulo ventas (objetos de transferencia): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("venta-dto")
package com.bomerp.ventas.venta.dto;

import org.springframework.modulith.NamedInterface;
