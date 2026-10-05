-- =========================================================
-- SCRIPT ADICIONAL PARA CATEGORIAS, PROVEEDORES Y USUARIOS
-- =========================================================

-- 1. Insertar Categorias
INSERT INTO CATEGORIAS (nombre, descripcion) VALUES ('Equipos Split', 'Aires acondicionados tipo split muro');
INSERT INTO CATEGORIAS (nombre, descripcion) VALUES ('Equipos Cassette', 'Aires acondicionados tipo cassette de techo');
INSERT INTO CATEGORIAS (nombre, descripcion) VALUES ('Repuestos', 'Repuestos y partes electromecanicas');
INSERT INTO CATEGORIAS (nombre, descripcion) VALUES ('Insumos', 'Gases, soldadura, tubos y aislamientos');

-- 2. Insertar Proveedores
INSERT INTO PROVEEDORES (tipo, nombre_o_razon_social, ruc, dni, direccion, telefono, correo, contacto_vendedor)
VALUES ('EMPRESA', 'York Peru S.A.C.', '20123456789', NULL, 'Av. Repblica de Panam 3535, San Isidro', '01-444-1234', 'ventas@york.pe', 'Luis Rojas');

INSERT INTO PROVEEDORES (tipo, nombre_o_razon_social, ruc, dni, direccion, telefono, correo, contacto_vendedor)
VALUES ('EMPRESA', 'RefriPeru S.A.', '20987654321', NULL, 'Calle Los Mirtos 120, Lince', '01-222-5555', 'pedidos@refriperu.com', 'Ana Maria');

-- 3. Insertar Usuarios
-- NOTA: Las contraseas estan encriptadas con BCrypt. 
-- El texto plano de la contrasea para ambos usuarios es: 123456
INSERT INTO USUARIOS (username, password, rol) 
VALUES ('admin', '\\/.688ZJc.F7F/Fj/OWr/E879x8l1O1jYg7t8Q9tXG.D2xKk.', 'ADMINISTRADOR');

INSERT INTO USUARIOS (username, password, rol) 
VALUES ('asistente', '\\/.688ZJc.F7F/Fj/OWr/E879x8l1O1jYg7t8Q9tXG.D2xKk.', 'ASISTENTE');

COMMIT;
