/**
 * API del módulo facturacion (entidades): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("notacredito-entity")
package com.bomerp.facturacion.notacredito.entity;

import org.springframework.modulith.NamedInterface;
