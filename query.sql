DROP DATABASE IF EXISTS tourinvest;

CREATE DATABASE tourinvest CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE tourinvest;

-- =========================================================
-- TABLAS (alineadas con el MER de la evidencia GA6-AA1-EV02)
-- =========================================================

CREATE TABLE roles (
    id_rol INT AUTO_INCREMENT PRIMARY KEY,
    nombre ENUM('Administrador','Analista','Inversionista') NOT NULL UNIQUE
);

CREATE TABLE usuarios (
    id_usuario INT AUTO_INCREMENT PRIMARY KEY,
    id_rol INT NOT NULL,
    nombre1 VARCHAR(30) NOT NULL,
    apellido1 VARCHAR(100) NOT NULL,
    cedula VARCHAR(20) NOT NULL UNIQUE,
    fecha_nacimiento DATE NOT NULL,
    correo VARCHAR(120) NOT NULL UNIQUE,
    contrasena VARCHAR(255) NOT NULL,
    estado ENUM('Activo','Inactivo') DEFAULT 'Activo',
    fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_usuario_rol FOREIGN KEY (id_rol) REFERENCES roles (id_rol) ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE empresas (
    id_empresa INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL,
    sector VARCHAR(80) NOT NULL,
    pais VARCHAR(80) NOT NULL,
    simbolo VARCHAR(10) NOT NULL UNIQUE
);

CREATE TABLE acciones (
    id_accion INT AUTO_INCREMENT PRIMARY KEY,
    id_empresa INT NOT NULL,
    precio DECIMAL(12, 2) NOT NULL,
    variacion DECIMAL(8, 2) DEFAULT 0,
    fecha_actualizacion DATETIME NOT NULL,
    CONSTRAINT fk_acciones_empresa FOREIGN KEY (id_empresa) REFERENCES empresas (id_empresa) ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE portafolios (
    id_portafolio INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    nombre VARCHAR(120) NOT NULL,
    fecha_creacion DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_portafolio_usuario FOREIGN KEY (id_usuario) REFERENCES usuarios (id_usuario) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE inversiones (
    id_inversion INT AUTO_INCREMENT PRIMARY KEY,
    id_portafolio INT NOT NULL,
    id_accion INT NOT NULL,
    cantidad INT NOT NULL,
    precio_compra DECIMAL(12, 2) NOT NULL,
    fecha_compra DATE NOT NULL,
    CONSTRAINT fk_inversion_portafolio FOREIGN KEY (id_portafolio) REFERENCES portafolios (id_portafolio) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_inversion_accion FOREIGN KEY (id_accion) REFERENCES acciones (id_accion) ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE alertas (
    id_alerta INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    id_accion INT NOT NULL,
    precio_objetivo DECIMAL(12, 2) NOT NULL,
    estado ENUM('Activa','Cumplida','Cancelada') DEFAULT 'Activa',
    CONSTRAINT fk_alerta_usuario FOREIGN KEY (id_usuario) REFERENCES usuarios (id_usuario) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_alerta_accion FOREIGN KEY (id_accion) REFERENCES acciones (id_accion) ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE notificaciones (
    id_notificacion INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    mensaje VARCHAR(255) NOT NULL,
    fecha DATETIME DEFAULT CURRENT_TIMESTAMP,
    leida BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_notificacion_usuario FOREIGN KEY (id_usuario) REFERENCES usuarios (id_usuario) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE reportes (
    id_reporte INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    id_empresa INT NOT NULL,
    titulo VARCHAR(150) NOT NULL,
    descripcion TEXT,
    fecha_generacion DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reporte_usuario FOREIGN KEY (id_usuario) REFERENCES usuarios (id_usuario) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_reporte_empresa FOREIGN KEY (id_empresa) REFERENCES empresas (id_empresa) ON UPDATE CASCADE ON DELETE RESTRICT
);

SHOW TABLES;

DESCRIBE roles;
DESCRIBE usuarios;
DESCRIBE empresas;
DESCRIBE acciones;
DESCRIBE portafolios;
DESCRIBE inversiones;
DESCRIBE alertas;
DESCRIBE notificaciones;
DESCRIBE reportes;

SELECT
    TABLE_NAME,
    COLUMN_NAME,
    CONSTRAINT_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE
    TABLE_SCHEMA = 'tourinvest'
    AND REFERENCED_TABLE_NAME IS NOT NULL;

-- =========================================================
-- SEEDS
-- =========================================================

INSERT INTO roles (nombre) VALUES
    ('Administrador'),
    ('Analista'),
    ('Inversionista');

INSERT INTO
    usuarios (id_rol, nombre1, apellido1, cedula, fecha_nacimiento, correo, contrasena)
VALUES
    -- Contrasena real para los 3: "123456".
    -- Hashes BCrypt con coste 10 (el mismo que usa PasswordEncoderConfig).
    -- Cada usuario tiene SU propio hash: sharing un unico hash entre cuentas
    -- permitiria comparar usuarios entre si ("si dos usuarios estan
    -- autenticandose, equals(hash) revela que es la misma contrasena").
    --   kike@    = Administrador (id_rol 1)
    --   marlen@  = Analista      (id_rol 2)
    --   juan@    = Inversionista (id_rol 3)
    -- Para cambiar la clave de un usuario, generarla con:
    --   BCryptPasswordEncoder(10).encode("nuevaClave")
    (1, 'Kike', 'Aguirre', '80760000', '1983-08-10', 'kike@tourinvest.com', '$2b$10$fbjJzIAH80WHpx0PnGhpfewc.lKDhBjQaj5GJq8Pdk9N9ny8nOxx2'),
    (2, 'Marlen', 'Murcia', '1001234568', '2000-05-15', 'marlen@tourinvest.com', '$2b$10$TplImVf8oPv4bpeii9kdr.x4kyeIiM62iz4774mbd/1F2hbpGoFDO'),
    (3, 'Juan', 'Fuentes', '1001234567', '1998-11-27', 'juan@tourinvest.com', '$2b$10$9jl9pH5V1l.aEbCx1sKv.OdkXLgpd6JbyuxJPqkah9SBMGCHBAfNq');

INSERT INTO
    empresas (nombre, sector, pais, simbolo)
VALUES
    ('Apple', 'Tecnología', 'Estados Unidos', 'AAPL'),
    ('Microsoft', 'Tecnología', 'Estados Unidos', 'MSFT'),
    ('Amazon', 'Comercio', 'Estados Unidos', 'AMZN'),
    ('Tesla', 'Automotriz', 'Estados Unidos', 'TSLA');

INSERT INTO
    acciones (id_empresa, precio, variacion, fecha_actualizacion)
VALUES
    (1, 195.50, 1.80, NOW()),
    (2, 432.15, -0.65, NOW()),
    (3, 176.40, 0.40, NOW()),
    (4, 250.80, 3.25, NOW());

INSERT INTO
    portafolios (id_usuario, nombre)
VALUES
    (3, 'Portafolio Principal'),
    (3, 'Portafolio Largo Plazo');

INSERT INTO
    inversiones (id_portafolio, id_accion, cantidad, precio_compra, fecha_compra)
VALUES
    (1, 1, 10, 180.00, '2025-03-15'),
    (1, 2, 5, 410.50, '2025-05-10'),
    (2, 4, 15, 240.00, '2025-06-18');

INSERT INTO
    alertas (id_usuario, id_accion, precio_objetivo)
VALUES
    (3, 1, 210),
    (3, 4, 300);

INSERT INTO
    reportes (id_usuario, id_empresa, titulo, descripcion)
VALUES
    (2, 1, 'Reporte Apple', 'La acción presenta tendencia alcista.'),
    (2, 4, 'Reporte Tesla', 'Incremento de volatilidad.');

-- =========================================================
-- CONSULTAS DE VERIFICACIÓN
-- =========================================================

SELECT * FROM usuarios;
SELECT * FROM empresas;
SELECT * FROM acciones;

SELECT * FROM empresas ORDER BY nombre;

SELECT * FROM empresas WHERE sector = 'Tecnología';

SELECT usuarios.*, roles.nombre AS rol
FROM usuarios
INNER JOIN roles ON usuarios.id_rol = roles.id_rol
WHERE roles.nombre = 'Administrador';

SELECT * FROM empresas WHERE pais = 'Estados Unidos';

SELECT empresas.nombre, acciones.precio, acciones.variacion
FROM acciones
    INNER JOIN empresas ON acciones.id_empresa = empresas.id_empresa;

SELECT usuarios.nombre1, portafolios.nombre
FROM usuarios
    INNER JOIN portafolios ON usuarios.id_usuario = portafolios.id_usuario;

SELECT usuarios.nombre1, empresas.nombre, inversiones.cantidad, inversiones.precio_compra
FROM
    inversiones
    INNER JOIN portafolios ON inversiones.id_portafolio = portafolios.id_portafolio
    INNER JOIN usuarios ON portafolios.id_usuario = usuarios.id_usuario
    INNER JOIN acciones ON inversiones.id_accion = acciones.id_accion
    INNER JOIN empresas ON acciones.id_empresa = empresas.id_empresa;

SELECT COUNT(*) AS TotalUsuarios FROM usuarios;
SELECT COUNT(*) AS TotalEmpresas FROM empresas;

SELECT AVG(precio) FROM acciones;
SELECT MAX(precio) FROM acciones;
SELECT MIN(precio) FROM acciones;

SELECT SUM(precio_compra * cantidad) AS TotalInvertido
FROM inversiones;

-- NOTA: las sentencias UPDATE/DELETE/TRIGGER de la seccion "PRUEBAS DE ESCRITURA"
-- se movieron a `sql/pruebas-escritura.sql` porque destruyen los datos semilla
-- (renombraban el correo del administrador y borraban alertas/reportes), lo que
-- hacia fallar las pruebas de Postman y el arranque de la app. Aqui el seed
-- queda LIMPIO: tras ejecutarlo, los 3 usuarios de prueba estan disponibles.

SELECT * FROM empresas WHERE nombre LIKE '%Apple%';
SELECT * FROM usuarios WHERE nombre1 LIKE '%Juan%';
SELECT * FROM empresas WHERE simbolo = 'AAPL';

SELECT sector, COUNT(*) FROM empresas GROUP BY sector;

SELECT roles.nombre AS rol, COUNT(*) AS cantidad
FROM usuarios
INNER JOIN roles ON usuarios.id_rol = roles.id_rol
GROUP BY roles.nombre;

SELECT sector, COUNT(*) cantidad
FROM empresas
GROUP BY sector
HAVING cantidad > 1;

-- =========================================================
-- VISTA
-- =========================================================

CREATE VIEW vista_portafolio AS
SELECT usuarios.nombre1, empresas.nombre, inversiones.cantidad, inversiones.precio_compra
FROM
    inversiones
    INNER JOIN portafolios ON inversiones.id_portafolio = portafolios.id_portafolio
    INNER JOIN usuarios ON usuarios.id_usuario = portafolios.id_usuario
    INNER JOIN acciones ON acciones.id_accion = inversiones.id_accion
    INNER JOIN empresas ON empresas.id_empresa = acciones.id_empresa;

SELECT * FROM vista_portafolio;

-- =========================================================
-- ÍNDICES
-- =========================================================

CREATE INDEX idx_empresa_nombre ON empresas (nombre);
CREATE INDEX idx_usuario_correo ON usuarios (correo);

-- =========================================================
-- PROCEDIMIENTO ALMACENADO
-- =========================================================

DELIMITER //

CREATE PROCEDURE MostrarEmpresas()
BEGIN
    SELECT * FROM empresas;
END //

DELIMITER ;

CALL MostrarEmpresas();

-- =========================================================
-- TRIGGER
-- =========================================================

DELIMITER //

CREATE TRIGGER validar_precio
BEFORE INSERT ON acciones
FOR EACH ROW
BEGIN
    IF NEW.precio <= 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'El precio debe ser mayor que cero';
    END IF;
END //

DELIMITER ;