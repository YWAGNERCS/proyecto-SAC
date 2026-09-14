package com.jdrefrigeracion.catalogo;

import com.jdrefrigeracion.catalogo.models.Producto;
import com.jdrefrigeracion.catalogo.repositories.ProductoRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@SpringBootTest
@Transactional
public class ProductoCrudTest {

    @Autowired
    private ProductoRepository productoRepository;

    @Test
    public void deberiaCrearYGuardarProducto() {
        Producto nuevoProducto = new Producto();
        nuevoProducto.setNombre("Gas Refrigerante R410A");
        nuevoProducto.setDescripcion("Cilindro de 11.3 kg");
        nuevoProducto.setSector(Producto.Sector.REFRIGERACION);
        nuevoProducto.setCostoBase(new BigDecimal("150.00"));
        nuevoProducto.setMargenPorcentaje(new BigDecimal("30.00"));
        nuevoProducto.setMoneda(Producto.Moneda.DOLARES);
        nuevoProducto.setStockActual(20);
        nuevoProducto.setStockMinimo(5);
        nuevoProducto.setVisibleEnCatalogo(true);

        Producto guardado = productoRepository.save(nuevoProducto);

        Assertions.assertNotNull(guardado.getId(), "El producto deberia tener un ID tras guardarse");
        Assertions.assertEquals("Gas Refrigerante R410A", guardado.getNombre());
    }

    @Test
    public void deberiaEncontrarProductoPorId() {
        Producto nuevoProducto = new Producto();
        nuevoProducto.setNombre("Compresor Inverter");
        nuevoProducto.setSector(Producto.Sector.CLIMATIZACION);
        nuevoProducto.setCostoBase(new BigDecimal("300.00"));
        nuevoProducto.setMargenPorcentaje(new BigDecimal("25.00"));
        nuevoProducto.setMoneda(Producto.Moneda.DOLARES);
        nuevoProducto.setStockActual(10);
        nuevoProducto.setStockMinimo(2);
        
        Producto guardado = productoRepository.save(nuevoProducto);

        Optional<Producto> encontrado = productoRepository.findById(guardado.getId());
        
        Assertions.assertTrue(encontrado.isPresent());
        Assertions.assertEquals("Compresor Inverter", encontrado.get().getNombre());
    }

    @Test
    public void deberiaActualizarStockDeProducto() {
        Producto nuevoProducto = new Producto();
        nuevoProducto.setNombre("Tubo de cobre 1/4");
        nuevoProducto.setSector(Producto.Sector.MATERIAL_SERVICIO);
        nuevoProducto.setCostoBase(new BigDecimal("15.00"));
        nuevoProducto.setMargenPorcentaje(new BigDecimal("20.00"));
        nuevoProducto.setMoneda(Producto.Moneda.SOLES);
        nuevoProducto.setStockActual(50);
        nuevoProducto.setStockMinimo(10);
        
        Producto guardado = productoRepository.save(nuevoProducto);

        // Simulamos una actualizacion de stock tras una venta
        guardado.setStockActual(guardado.getStockActual() - 5);
        Producto actualizado = productoRepository.save(guardado);

        Assertions.assertEquals(45, actualizado.getStockActual(), "El stock deberia reducirse a 45");
    }
}
