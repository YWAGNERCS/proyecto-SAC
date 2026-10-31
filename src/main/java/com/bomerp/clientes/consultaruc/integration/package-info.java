/**
 * API del módulo clientes (integración): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("consultaruc-integration")
package com.bomerp.clientes.consultaruc.integration;

import org.springframework.modulith.NamedInterface;
