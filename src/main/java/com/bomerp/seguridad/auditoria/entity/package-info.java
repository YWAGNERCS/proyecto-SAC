/**
 * API del módulo seguridad (entidades): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("auditoria-entity")
package com.bomerp.seguridad.auditoria.entity;

import org.springframework.modulith.NamedInterface;
