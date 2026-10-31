package com.bomerp;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;

/**
 * ADR-002: cada paquete directo de com.bomerp es un módulo (ventas, cobranza, facturacion...).
 * Esta prueba falla si un módulo usa algo interno de otro (su repository o controller: solo se
 * pueden usar los paquetes marcados con @NamedInterface en package-info) o si dos módulos se
 * necesitan mutuamente (ciclo). Para avisar a otro módulo sin depender de él se publica un evento
 * (VentaRegistradaEvent, VentaAnuladaEvent).
 */
class ModularityTests {

    private final ApplicationModules modulos = ApplicationModules.of(BomerpApplication.class);

    @Test
    void losModulosRespetanSusLimites() {
        modulos.verify();
    }

    /**
     * Documentación: diagramas PlantUML de Spring Modulith y un diagrama Mermaid (el que usa el sitio)
     * en target/spring-modulith-docs.
     */
    @Test
    void generaElDiagramaDeModulos() throws IOException {
        new Documenter(modulos)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();

        TreeSet<String> flechas = new TreeSet<>();
        for (ApplicationModule m : modulos) {
            m.getDirectDependencies(modulos).stream()
                    .map(d -> d.getTargetModule().getIdentifier().toString())
                    .filter(destino -> !destino.equals(m.getIdentifier().toString()))
                    .forEach(destino -> flechas.add("    " + m.getIdentifier() + " --> " + destino));
        }
        Path salida = Path.of("target", "spring-modulith-docs", "modulos.mmd");
        Files.createDirectories(salida.getParent());
        Files.writeString(salida, "flowchart LR\n" + String.join("\n", flechas) + "\n", StandardCharsets.UTF_8);
    }
}
