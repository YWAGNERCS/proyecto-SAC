/**
 * API del módulo ventas (eventos): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("venta-event")
package com.bomerp.ventas.venta.event;

import org.springframework.modulith.NamedInterface;
