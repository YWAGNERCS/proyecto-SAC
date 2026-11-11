package com.bomerp.config.monitoreo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * BD2 S14: que Oracle sepa qué operación del sistema usa cada conexión.
 *
 * Todas las conexiones del backend entran con el mismo usuario. Sin esto, en V$SESSION, en ASH y en la columna
 * PROGRAMA de VENTA_AUDITORIA todas se ven iguales ("JDBC Thin Client"), y ante un bloqueo no se sabe qué pantalla
 * bloquea a cuál. Con esto, la sesión que espera dice, por ejemplo, MODULE = "SGE-JD ventas" y
 * ACTION = "PATCH /ventas/182/anular".
 *
 * Cómo: un filtro anota la operación REST en el hilo; el DataSource la pasa al driver al entregar la conexión
 * (OCSID.MODULE y OCSID.ACTION, métricas de punta a punta de Oracle). El driver la envía junto con la siguiente
 * sentencia, sin un viaje extra a la base. Fuera de una petición (tareas programadas): MODULE = "SGE-JD tareas".
 * Una conexión libre en el pool conserva la última operación; su STATUS = INACTIVE indica que ya terminó.
 */
public final class ModuloOracle {

    // Límites de Oracle: MODULE 48 bytes, ACTION 32
    private static final int MAX_MODULE = 48;
    private static final int MAX_ACTION = 32;
    private static final ThreadLocal<String[]> ACTUAL = new ThreadLocal<>();

    private ModuloOracle() {
    }

    /**
     * Anota en el hilo la operación REST que se está atendiendo. Es un filtro y no un interceptor de Spring MVC:
     * corre ANTES que la seguridad, que también usa la base (registra en HISTORIAL_CAMBIOS los intentos
     * denegados). Con un interceptor, esa conexión quedaba sin marca (primera prueba en desarrollo, 10/11/2026).
     * La acción lleva la URL real (con el id), que en un bloqueo dice qué venta o qué cobro.
     */
    public static class Filtro extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
                throws ServletException, IOException {
            String ruta = request.getRequestURI().replaceFirst("^/api/v\\d+", "").replaceFirst("^/admin", "");
            String[] partes = ruta.split("/");
            String modulo = "SGE-JD " + (partes.length > 1 && !partes[1].isBlank() ? partes[1] : "web");
            ACTUAL.set(new String[]{recortar(modulo, MAX_MODULE), recortar(request.getMethod() + " " + ruta, MAX_ACTION)});
            try {
                cadena.doFilter(request, response);
            } finally {
                ACTUAL.remove();
            }
        }
    }

    /** Envuelve el DataSource de Spring Boot para marcar cada conexión que entrega. */
    public static class Marcador implements BeanPostProcessor {
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof DataSource ds && !(bean instanceof ConMarca) && "dataSource".equals(beanName)) {
                return new ConMarca(ds);
            }
            return bean;
        }
    }

    static class ConMarca extends DelegatingDataSource {
        ConMarca(DataSource destino) {
            super(destino);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return marcar(super.getConnection());
        }

        @Override
        public Connection getConnection(String usuario, String clave) throws SQLException {
            return marcar(super.getConnection(usuario, clave));
        }
    }

    static Connection marcar(Connection conexion) {
        String[] actual = ACTUAL.get();
        String modulo = actual != null ? actual[0] : "SGE-JD tareas";
        String accion = actual != null ? actual[1] : recortar(Thread.currentThread().getName(), MAX_ACTION);
        try {
            conexion.setClientInfo("OCSID.MODULE", modulo);
            conexion.setClientInfo("OCSID.ACTION", accion);
        } catch (SQLException | RuntimeException e) {
            // Otro motor (H2 en algunas pruebas) no conoce estas claves: no es un error, solo no hay marca
        }
        return conexion;
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
