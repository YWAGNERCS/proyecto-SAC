/**
 * API del módulo catalogo (servicios (fachadas)): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("producto-service")
package com.bomerp.catalogo.producto.service;

import org.springframework.modulith.NamedInterface;
