/**
 * API del módulo facturacion (objetos de transferencia): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("notacredito-dto")
package com.bomerp.facturacion.notacredito.dto;

import org.springframework.modulith.NamedInterface;
