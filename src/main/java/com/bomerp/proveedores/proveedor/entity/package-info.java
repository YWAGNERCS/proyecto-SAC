/**
 * API del módulo proveedores (entidades): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("proveedor-entity")
package com.bomerp.proveedores.proveedor.entity;

import org.springframework.modulith.NamedInterface;
