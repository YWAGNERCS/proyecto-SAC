/**
 * API del módulo clientes (servicios (fachadas)): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("sede-service")
package com.bomerp.clientes.sede.service;

import org.springframework.modulith.NamedInterface;
