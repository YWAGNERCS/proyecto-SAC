/**
 * API del módulo proveedores (servicios (fachadas)): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("proveedor-service")
package com.bomerp.proveedores.proveedor.service;

import org.springframework.modulith.NamedInterface;
