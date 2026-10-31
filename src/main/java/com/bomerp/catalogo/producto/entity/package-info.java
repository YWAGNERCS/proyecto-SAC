/**
 * API del módulo catalogo (entidades): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("producto-entity")
package com.bomerp.catalogo.producto.entity;

import org.springframework.modulith.NamedInterface;
