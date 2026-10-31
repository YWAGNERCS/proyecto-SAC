-- Limpieza de datos de SGE-JD (esquema JDREFRIGERACION).
-- No se ejecuta solo: lo llama base_limpia.ps1, que primero saca un respaldo con Data Pump.
--
--   @base_limpia.sql <MODO> <FIN>
--   MODO  PRUEBA  : borra solo lo marcado "[Prueba]" (clientes, proveedores, productos,
--                   categorías) y todo lo que cuelga de ellos (también sus sedes, equipos
--                   instalados, contratos y órdenes de trabajo, y las órdenes que usaron un
--                   producto [Prueba]). Devuelve al stock lo que esas ventas, compras y órdenes
--                   movieron. Lo demás queda igual.
--         ENTREGA : deja la base lista para el cliente: borra TODOS los movimientos
--                   (proformas, ventas, comprobantes, notas de crédito, cobros, devoluciones, compras,
--                   mantenimientos, órdenes de trabajo, contratos y equipos instalados) y el historial
--                   de cambios, borra lo
--                   marcado "[Prueba]", reinicia las series de comprobantes y los contadores de id.
--                   Conserva catálogo, clientes (con sus sedes) y proveedores reales. El stock queda
--                   como está: se ajusta con el inventario físico.
--   FIN   COMMIT o ROLLBACK (ROLLBACK = ensayo: muestra cuánto borraría y no cambia nada).
--
-- Orden de borrado según las llaves foráneas (de hijos a padres):
--   (se sueltan antes las referencias circulares orden ↔ equipo ↔ mantenimiento)
--   devoluciones → notas_credito → comprobantes → detalle_cobro → cobros → mantenimientos
--   → materiales_orden → ordenes_trabajo → equipos_instalados → contratos_mantenimiento
--   → detalle_venta → ventas → items_proforma → proformas → detalle_compra → compras
--   → sedes_cliente → productos → categorías, clientes, proveedores.
whenever sqlerror exit failure rollback
set serveroutput on feedback off verify off
alter session set current_schema = JDREFRIGERACION;

declare
    modo constant varchar2(10) := upper('&1');
    fin  constant varchar2(10) := upper('&2');
    marca constant varchar2(10) := '[Prueba]%';

    v_cli  sys.odcinumberlist; v_prov sys.odcinumberlist; v_prod sys.odcinumberlist;
    v_cat  sys.odcinumberlist; v_pf   sys.odcinumberlist; v_ven  sys.odcinumberlist;
    v_cob  sys.odcinumberlist; v_com  sys.odcinumberlist; v_pf2 sys.odcinumberlist;
    v_ord  sys.odcinumberlist; v_eq   sys.odcinumberlist; v_con  sys.odcinumberlist;

    procedure contar(t varchar2, n number) is
    begin dbms_output.put_line(rpad('  ' || t, 22) || lpad(n, 6)); end;
begin
    if modo not in ('PRUEBA', 'ENTREGA') or fin not in ('COMMIT', 'ROLLBACK') then
        raise_application_error(-20001, 'Uso: @base_limpia.sql PRUEBA|ENTREGA COMMIT|ROLLBACK');
    end if;

    -- 1) Maestros marcados [Prueba]
    select id bulk collect into v_cli  from clientes    where nombreorazon_social   like marca;
    select id bulk collect into v_prov from proveedores where nombre_o_razon_social like marca;
    select id bulk collect into v_prod from productos   where nombre like marca;
    select id bulk collect into v_cat  from categorias  where nombre like marca;

    -- 2) Movimientos a borrar
    if modo = 'ENTREGA' then
        select id bulk collect into v_pf  from proformas;
        select id bulk collect into v_ven from ventas;
        select id bulk collect into v_cob from cobros;
        select id bulk collect into v_com from compras;
        select id bulk collect into v_ord from ordenes_trabajo;
        select id bulk collect into v_eq  from equipos_instalados;
        select id bulk collect into v_con from contratos_mantenimiento;
    else
        -- proformas y ventas de un cliente [Prueba] o con algún producto [Prueba]
        select id bulk collect into v_pf from proformas p
         where p.cliente_id in (select column_value from table(v_cli))
            or exists (select 1 from items_proforma i where i.proforma_id = p.id
                          and i.producto_id in (select column_value from table(v_prod)));
        select id bulk collect into v_ven from ventas v
         where v.cliente_id in (select column_value from table(v_cli))
            or v.proforma_id in (select column_value from table(v_pf))
            or exists (select 1 from detalle_venta d where d.venta_id = v.id
                          and d.producto_id in (select column_value from table(v_prod)));
        -- la proforma de origen de esas ventas también es de prueba
        -- (en otra colección: Oracle vacía el destino de un BULK COLLECT antes de leer)
        select id bulk collect into v_pf2 from proformas p
         where p.id in (select column_value from table(v_pf))
            or p.id in (select proforma_id from ventas where id in (select column_value from table(v_ven)));
        v_pf := v_pf2;
        -- un cobro se borra entero si es de un cliente [Prueba] o paga alguna de esas ventas
        select id bulk collect into v_cob from cobros c
         where c.cliente_id in (select column_value from table(v_cli))
            or exists (select 1 from detalle_cobro d where d.cobro_id = c.id
                          and d.venta_id in (select column_value from table(v_ven)));
        select id bulk collect into v_com from compras c
         where c.proveedor_id in (select column_value from table(v_prov))
            or exists (select 1 from detalle_compra d where d.compra_id = c.id
                          and d.producto_id in (select column_value from table(v_prod)));

        -- órdenes de un cliente [Prueba] o que usaron un material [Prueba]; equipos y contratos de esos clientes
        -- o con un equipo [Prueba]
        select id bulk collect into v_ord from ordenes_trabajo o
         where o.cliente_id in (select column_value from table(v_cli))
            or exists (select 1 from materiales_orden m where m.orden_id = o.id
                          and m.producto_id in (select column_value from table(v_prod)));
        select id bulk collect into v_eq from equipos_instalados e
         where e.cliente_id in (select column_value from table(v_cli))
            or e.producto_id in (select column_value from table(v_prod));
        select id bulk collect into v_con from contratos_mantenimiento k
         where k.cliente_id in (select column_value from table(v_cli));

        -- Devolver al stock (solo productos que se quedan y que controlan stock, como en VentaService):
        -- una venta REGISTRADA lo descontó (una ANULADA ya lo devolvió), una compra lo sumó y una
        -- orden REALIZADA descontó sus materiales.
        update productos p
           set stock_actual = nvl(stock_actual, 0)
               + nvl((select sum(d.cantidad) from detalle_venta d join ventas v on v.id = d.venta_id
                       where d.producto_id = p.id and v.estado = 'REGISTRADA'
                         and v.id in (select column_value from table(v_ven))), 0)
               - nvl((select sum(d.cantidad) from detalle_compra d
                       where d.producto_id = p.id
                         and d.compra_id in (select column_value from table(v_com))), 0)
               + nvl((select sum(m.cantidad) from materiales_orden m join ordenes_trabajo o on o.id = m.orden_id
                       where m.producto_id = p.id and o.estado = 'REALIZADA'
                         and o.id in (select column_value from table(v_ord))), 0)
         where p.sector = 'MATERIAL_SERVICIO'
           and p.id not in (select column_value from table(v_prod));
        dbms_output.put_line('Modo PRUEBA: stock recalculado en ' || sql%rowcount || ' materiales.');
    end if;

    dbms_output.put_line('Modo ' || modo || ' (' || fin || '). Filas borradas:');
    -- devoluciones de ventas anuladas (las de un cliente [Prueba] caen con su venta)
    delete from devoluciones where venta_id in (select column_value from table(v_ven))
                                or cliente_id in (select column_value from table(v_cli));
    contar('devoluciones', sql%rowcount);
    delete from notas_credito where comprobante_id in
        (select id from comprobantes where venta_id in (select column_value from table(v_ven)));
    contar('notas_credito', sql%rowcount);
    delete from comprobantes where venta_id in (select column_value from table(v_ven));
    contar('comprobantes', sql%rowcount);
    delete from detalle_cobro where cobro_id in (select column_value from table(v_cob))
                                 or venta_id in (select column_value from table(v_ven));
    contar('detalle_cobro', sql%rowcount);
    delete from cobros where id in (select column_value from table(v_cob));
    contar('cobros', sql%rowcount);
    -- Servicios: primero se sueltan las referencias circulares (orden ↔ equipo ↔ mantenimiento ↔ venta)
    -- de lo que se queda hacia lo que se borra
    update mantenimientos set orden_trabajo_id = null where orden_trabajo_id in (select column_value from table(v_ord));
    update equipos_instalados set orden_trabajo_id = null where orden_trabajo_id in (select column_value from table(v_ord));
    update equipos_instalados set venta_id = null where venta_id in (select column_value from table(v_ven));
    update ordenes_trabajo set equipo_id = null where equipo_id in (select column_value from table(v_eq));
    update ordenes_trabajo set venta_id = null where venta_id in (select column_value from table(v_ven));
    update ordenes_trabajo set venta_facturada_id = null where venta_facturada_id in (select column_value from table(v_ven));
    update ordenes_trabajo set mantenimiento_id = null where mantenimiento_id in
        (select id from mantenimientos where venta_id in (select column_value from table(v_ven))
            or equipo_id in (select column_value from table(v_eq))
            or contrato_id in (select column_value from table(v_con))
            or cliente_id in (select column_value from table(v_cli)));
    delete from mantenimientos where venta_id in (select column_value from table(v_ven))
                                  or equipo_id in (select column_value from table(v_eq))
                                  or contrato_id in (select column_value from table(v_con))
                                  or cliente_id in (select column_value from table(v_cli));
    contar('mantenimientos', sql%rowcount);
    delete from materiales_orden where orden_id in (select column_value from table(v_ord));
    contar('materiales_orden', sql%rowcount);
    delete from ordenes_trabajo where id in (select column_value from table(v_ord));
    contar('ordenes_trabajo', sql%rowcount);
    delete from equipos_instalados where id in (select column_value from table(v_eq));
    contar('equipos_instalados', sql%rowcount);
    delete from contratos_mantenimiento where id in (select column_value from table(v_con));
    contar('contratos', sql%rowcount);
    delete from detalle_venta where venta_id in (select column_value from table(v_ven));
    contar('detalle_venta', sql%rowcount);
    delete from ventas where id in (select column_value from table(v_ven));
    contar('ventas', sql%rowcount);
    delete from items_proforma where proforma_id in (select column_value from table(v_pf));
    contar('items_proforma', sql%rowcount);
    delete from proformas where id in (select column_value from table(v_pf));
    contar('proformas', sql%rowcount);
    delete from detalle_compra where compra_id in (select column_value from table(v_com));
    contar('detalle_compra', sql%rowcount);
    delete from compras where id in (select column_value from table(v_com));
    contar('compras', sql%rowcount);
    -- Las sedes son datos del cliente: solo se borran las de un cliente [Prueba]
    delete from sedes_cliente where cliente_id in (select column_value from table(v_cli));
    contar('sedes_cliente', sql%rowcount);
    delete from productos where id in (select column_value from table(v_prod));
    contar('productos', sql%rowcount);
    -- una categoría [Prueba] solo se borra si ya no le queda ningún producto real
    delete from categorias c where c.id in (select column_value from table(v_cat))
       and not exists (select 1 from productos p where p.categoria_id = c.id);
    contar('categorias', sql%rowcount);
    delete from clientes where id in (select column_value from table(v_cli));
    contar('clientes', sql%rowcount);
    delete from proveedores where id in (select column_value from table(v_prov));
    contar('proveedores', sql%rowcount);

    if modo = 'ENTREGA' then
        update series_comprobante set ultimo_numero = 0;
        contar('series reiniciadas', sql%rowcount);
        -- El historial de cambios de las pruebas no le sirve al cliente: arranca vacío.
        -- (En modo PRUEBA se conserva: es el registro de seguridad.)
        delete from historial_cambios;
        contar('historial_cambios', sql%rowcount);
    end if;

    if fin = 'COMMIT' then
        commit;
        dbms_output.put_line('Cambios guardados.');
        if modo = 'ENTREGA' then
            -- Las tablas de movimientos quedaron vacías: su próximo id vuelve a 1.
            -- (DDL: va después del COMMIT porque Oracle confirma solo al ejecutarlo.)
            for t in (select column_value nombre from table(sys.odcivarchar2list(
                        'DEVOLUCIONES', 'NOTAS_CREDITO', 'COMPROBANTES', 'DETALLE_COBRO', 'COBROS', 'MANTENIMIENTOS',
                        'DETALLE_VENTA', 'VENTAS', 'ITEMS_PROFORMA', 'PROFORMAS',
                        'DETALLE_COMPRA', 'COMPRAS', 'MATERIALES_ORDEN', 'ORDENES_TRABAJO',
                        'EQUIPOS_INSTALADOS', 'CONTRATOS_MANTENIMIENTO', 'HISTORIAL_CAMBIOS'))) loop
                execute immediate 'alter table ' || t.nombre
                    || ' modify id generated always as identity (start with 1)';
            end loop;
            dbms_output.put_line('Contadores de id de movimientos reiniciados en 1.');
        end if;
    else
        rollback;
        dbms_output.put_line('Ensayo: no se cambió nada (ROLLBACK).');
    end if;
end;
/
exit
