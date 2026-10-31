/**
 * API del módulo seguridad (pieza compartida): otros módulos pueden usar estos tipos (ADR-002).
 * Lo que no está marcado así (repository, controller) es interno y Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("descuento")
package com.bomerp.seguridad.descuento;

import org.springframework.modulith.NamedInterface;
