/**
 * API del módulo seguridad (pieza compartida): qué puede hacer el usuario de la petición actual según su rol y
 * sus permisos delegados. Otros módulos la usan para reglas que no se resuelven con una ruta en SecurityConfig
 * (p. ej. Ventas: registrar una venta con fecha pasada). Spring Modulith lo verifica en ModularityTests.
 */
@NamedInterface("acceso")
package com.bomerp.seguridad.acceso;

import org.springframework.modulith.NamedInterface;
