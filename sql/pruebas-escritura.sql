-- =========================================================
-- TourInvest — PRUEBAS DE ESCRITURA (OPCIONAL / DESTRUCTIVO)
-- =========================================================
-- Estas sentencias ANTES vivian en `query.sql`. Se movieron aqui porque
-- MODIFICAN los datos semilla y dejaban la base en un estado que rompia las
-- pruebas de Postman y el login:
--     * renombraban el correo del administrador (kike@ -> kike@)
--     * borraban la alerta 2 y el reporte 2
--     * dejaban la alerta 1 en estado 'Cumplida'
--
-- PARA QUE SE USA: solo como evidencia de sentencia y de disparador (trigger)
-- en la materia. NO lo ejecutes antes de las pruebas de la API.
--
-- COMO VOLVER ATRAS (deja la base como recien sembrada):
--     mysql -u root < query.sql
--
-- Linux/macOS:  mysql -u root tourinvest < sql/pruebas-escritura.sql
-- Windows:     Get-Content sql\pruebas-escritura.sql | mysql -u root tourinvest
-- =========================================================

USE tourinvest;

-- =========================================================
-- SENTENCIA UPDATE
-- =========================================================
UPDATE acciones
SET precio = 210 WHERE id_accion = 1;

-- Renombra el correo del administrador: por eso se ejecuta SOLO aqui.
UPDATE usuarios
SET correo = 'kike@tourinvest.com' WHERE id_usuario = 1;

UPDATE alertas
SET estado = 'Cumplida' WHERE id_alerta = 1;

-- =========================================================
-- SENTENCIA DELETE
-- =========================================================
DELETE FROM reportes WHERE id_reporte = 2;
DELETE FROM alertas WHERE id_alerta = 2;

-- =========================================================
-- TRIGGER (validacion de precio al insertar una accion)
-- =========================================================
-- Nota: este trigger ya viene creado por query.sql. Si lo reejecutas sobre una
-- base ya sembrada MySQL respondera «Trigger already exists»; es esperado.
DROP TRIGGER IF EXISTS validar_precio;

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

-- =========================================================
-- VERIFICACION
-- =========================================================
SELECT id_accion, precio FROM acciones WHERE id_accion = 1;
SELECT id_usuario, correo FROM usuarios WHERE id_usuario = 1;
SELECT id_alerta, estado FROM alertas;
SELECT id_reporte, titulo FROM reportes;