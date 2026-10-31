/**
 * API del módulo clientes (objetos de transferencia): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("sede-dto")
package com.bomerp.clientes.sede.dto;

import org.springframework.modulith.NamedInterface;
