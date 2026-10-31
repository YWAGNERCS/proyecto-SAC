/**
 * API del módulo servicios (entidades): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("orden-entity")
package com.bomerp.servicios.orden.entity;

import org.springframework.modulith.NamedInterface;
