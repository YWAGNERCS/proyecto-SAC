/**
 * API del módulo catalogo (objetos de transferencia): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("producto-dto")
package com.bomerp.catalogo.producto.dto;

import org.springframework.modulith.NamedInterface;
