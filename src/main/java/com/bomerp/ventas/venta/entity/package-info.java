/**
 * API del módulo ventas (entidades): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("venta-entity")
package com.bomerp.ventas.venta.entity;

import org.springframework.modulith.NamedInterface;
