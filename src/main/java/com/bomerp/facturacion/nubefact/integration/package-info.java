/**
 * API del módulo facturacion (integración): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("nubefact-integration")
package com.bomerp.facturacion.nubefact.integration;

import org.springframework.modulith.NamedInterface;
