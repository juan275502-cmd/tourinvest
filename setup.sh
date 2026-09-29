#!/usr/bin/env bash
# =============================================================================
#  TourInvest — setup.sh   (LINUX)
# =============================================================================
#  Script de arranque del proyecto: diagnostico, base de datos y servidores.
#
#  REQUISITOS (todo se instala en Linux):
#    JDK 17+         apt install openjdk-17-jdk  |  dnf install java-17-openjdk-devel
#    Maven           apt install maven            |  dnf install maven
#    MySQL/MariaDB   apt install mysql-server     |  dnf install mariadb-server
#    Python 3        apt install python3          |  dnf install python3
#
#  COMANDOS:
#    ./setup.sh check             # verifica java/mvn/mysql/node/python Y la conexion a la BD
#    ./setup.sh seed              # carga query.sql (raiz) en la BD 'tourinvest'
#    ./setup.sh run-backend       # compila y arranca Spring Boot (API en :8080)
#    ./setup.sh run-backend-fast  # empaqueta el JAR y arranca con 'java -jar' (mas rapido)
#    ./setup.sh run-frontend      # sirve ./frontend en el puerto 80 y abre el navegador
#    ./setup.sh run               # ARRANCA EL PROYECTO COMPLETO (API + sitio)
#    ./setup.sh help
#
#  URL LIMPIA (sin :80): el sitio se sirve en el puerto 80, asi que en el
#  navegador se ve http://www.tourinvest.com  (parece un dominio de verdad).
#  Si el 80 esta ocupado o no tienes root, cambia PUERTO_WEB=8081 en .env.
#
#  CONVENCION DE HOSTNAME (no improvisar):
#    * Direccion real: SIEMPRE 127.0.0.1. Nunca 'localhost': en IPv6 resuelve a
#      ::1 y rompe JDBC/CORS de forma intermitente.
#    * Nombre visible: www.tourinvest.com, mapeado a 127.0.0.1 en /etc/hosts.
# =============================================================================

set -u

ROOT="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$ROOT/logs"

# --- Direcciones y puertos del proyecto -------------------------------------
SITIO="www.tourinvest.com"         # nombre visible en el navegador
IP_LOOPBACK="127.0.0.1"           # direccion real (loopback IPv4)
PUERTO_API=8080                    # API Spring Boot
PAGINA_INICIO="index.html"   # punto de entrada de la aplicacion

# --- Python: preferimos el .venv del repositorio, si no el del sistema -------
PY=""
for _cand in "$ROOT/.venv/bin/python" "$(command -v python3 2>/dev/null || true)" "$(command -v python 2>/dev/null || true)"; do
  if [ -n "$_cand" ] && [ -x "$_cand" ]; then PY="$_cand"; break; fi
done

# --- Carga opcional del archivo .env (si existe) -----------------------------
# Permite definir DB_PASSWORD, JWT_SECRET, etc. SIN escribirlos en
# application.properties (que va versionado en git).
if [ -f "$ROOT/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$ROOT/.env"
  set +a
  echo "  (configuracion leida desde .env)"
fi

DB_HOST="${DB_HOST:-$IP_LOOPBACK}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-tourinvest}"
DB_USER="${DB_USER:-root}"

# El sitio se sirve en el puerto 80 para que la URL sea limpia y parezca de
# dominio real: http://www.tourinvest.com  (sin :80 que ver).
# Cámbialo en .env (PUERTO_WEB=8081) si el 80 está ocupado por otro servicio.
PUERTO_WEB="${PUERTO_WEB:-80}"

# Credenciales de prueba sembradas por query.sql (clave 123456).
# Estos son los roles REALES segun query.sql (id_rol 1/2/3):
#   Administrador = kike@, Analista = marlen@, Inversionista = juan@
USUARIOS_SEED="    kike@tourinvest.com     Administrador
    marlen@tourinvest.com   Analista
    juan@tourinvest.com     Inversionista"

# -----------------------------------------------------------------------------
#  UTILIDADES
# -----------------------------------------------------------------------------

# Comprueba si el puerto TCP responde (sin depender de mysqladmin).
puerto_abierto() {
  local host="$1" port="$2"
  if command -v nc >/dev/null 2>&1; then
    nc -z -w 2 "$host" "$port" >/dev/null 2>&1
  else
    (exec 3<>"/dev/tcp/$host/$port") >/dev/null 2>&1
  fi
}

# URL del sitio. En el puerto 80 NO se escribe ":80": se ve como un dominio
# de verdad (http://www.tourinvest.com).
url_web() {
  local ruta="${1:-}"
  if [ "$PUERTO_WEB" = "80" ]; then
    echo "http://$SITIO$ruta"
  else
    echo "http://$SITIO:$PUERTO_WEB$ruta"
  fi
}

# En Linux los puertos <1024 solo los puede abrir root. Esta funcion decide si
# hace falta `sudo` para servir el sitio, y si no se puede, explica las dos
# opciones. Imprime el prefijo de comando en PREFIJO_WEB (vacio = sin sudo).
PREFIJO_WEB=""
resolver_privilegios_web() {
  PREFIJO_WEB=""
  [ "$PUERTO_WEB" -ge 1024 ] && return 0
  [ "$(id -u)" -eq 0 ] && return 0
  # root no es estrictamente necesario si al interprete se le dio el permiso.
  if "$PY" -c "import socket,sys;s=socket.socket();s.bind(('$IP_LOOPBACK',$PUERTO_WEB));s.close()" >/dev/null 2>&1; then
    return 0
  fi
  if sudo -n true >/dev/null 2>&1; then
    PREFIJO_WEB="sudo -n"
    return 0
  fi
  echo "  [X] El puerto $PUERTO_WEB necesita privilegios de root en Linux." >&2
  echo "      Elige una de estas dos opciones:" >&2
  echo "        A) Permiso permanente (solo una vez, recomendado):" >&2
  echo "             sudo setcap 'cap_net_bind_service=+ep' $(readlink -f "$PY")" >&2
  echo "        B) Levantar el proyecto como root (pide contrasena):" >&2
  echo "             sudo ./setup.sh run" >&2
  echo "      O usa otro puerto en .env:  PUERTO_WEB=8081" >&2
  return 1
}

# Crea el archivo .env con los valores por defecto si todavia no existe.
# .env esta en .gitignore: la contrasena nunca se versiona.
crear_env_si_falta() {
  [ -f "$ROOT/.env" ] && return 0
  cat > "$ROOT/.env" <<EOF
# TourInvest — configuracion local (creada por setup.sh)
DB_HOST=$DB_HOST
DB_PORT=$DB_PORT
DB_NAME=$DB_NAME
DB_USER=$DB_USER
DB_PASSWORD=
EOF
}

# Guarda un valor en el archivo .env de la raiz. Reescribe el archivo con un
# temporal en vez de usar `sed -i`, porque este ultimo falla con
# «Operation not permitted» cuando el archivo pertenece a otro usuario aunque
# el directorio sea escribible.
guardar_en_env() {
  local clave="$1" valor="$2"
  crear_env_si_falta || return 1

  local tmp clave_escapada
  tmp="$(mktemp)" || return 1
  clave_escapada="$(printf '%s' "$clave" | sed 's/[\\/&]/\\&/g')"
  awk -v k="$clave_escapada" -v v="$valor" '
    BEGIN { FS="=" }
    $1 == k { print k "=" v; seen=1; next }
    { print }
    END { if (!seen) print k "=" v }
  ' "$ROOT/.env" > "$tmp" && cat "$tmp" > "$ROOT/.env"
  rm -f "$tmp"
  return 0
}

# Comprobacion real de credenciales contra MySQL. Reutilizada por check/seed/run-*.
mysql_autentica() {
  local pw="$1"
  if [ -n "$pw" ]; then
    MYSQL_PWD="$pw" mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -e "SELECT 1;" >/dev/null 2>&1
  else
    mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -e "SELECT 1;" >/dev/null 2>&1
  fi
}

# Indica a que IP resuelve el sitio canonico (getent es el estandar en Linux).
ip_del_sitio() {
  if command -v getent >/dev/null 2>&1; then
    getent hosts "$SITIO" 2>/dev/null | awk '{print $1}' | head -n1
  else
    ping -c 1 -W 1 "$SITIO" 2>/dev/null | sed -n 's/.*(\([0-9.]*\)).*/\1/p' | head -n1
  fi
}

# www.tourinvest.com DEBE apuntar a 127.0.0.1. Si no, el navegador se ira a
# Internet (hoy ese dominio resuelve a un IP publico) y la app no respondera.
verificar_hosts() {
  local ip
  ip="$(ip_del_sitio)"
  if [ -z "$ip" ]; then
    echo "  [--] '$SITIO' no resuelve en este equipo."
    echo "      Anadelo al archivo de hosts de Linux:"
    echo "          $IP_LOOPBACK $SITIO"
    echo "      Comando: echo \"$IP_LOOPBACK $SITIO\" | sudo tee -a /etc/hosts"
    return 0   # no es bloqueante: la API igual funciona por 127.0.0.1
  fi
  if [ "$ip" = "$IP_LOOPBACK" ]; then
    echo "  [OK] $SITIO -> $ip"
    return 0
  fi
  echo "  [X] $SITIO resuelve a $ip, deberia ser $IP_LOOPBACK." >&2
  echo "      Anade esta linea al archivo de hosts de Linux:" >&2
  echo "          $IP_LOOPBACK $SITIO" >&2
  echo "      Comando: echo \"$IP_LOOPBACK $SITIO\" | sudo tee -a /etc/hosts" >&2
  return 1
}

# Explica por que falla la conexion: es el error mas comun de arranque.
diagnosticar_bd() {
  echo ""
  echo "  [X] No hay conexion con MySQL en $DB_HOST:$DB_PORT" >&2
  echo "" >&2
  if ! command -v mysql >/dev/null 2>&1; then
    echo "  Causa probable: el CLIENTE mysql no esta instalado." >&2
    echo "    Debian/Ubuntu : sudo apt install mysql-client" >&2
    echo "    Fedora/RHEL   : sudo dnf install mysql" >&2
  else
    echo "  Causa mas probable: el SERVIDOR MySQL no esta corriendo." >&2
    echo "    Debian/Ubuntu : sudo systemctl start mysql" >&2
    echo "    Fedora/RHEL   : sudo systemctl start mysqld" >&2
    echo "    O bien        : sudo service mysql start" >&2
    echo "" >&2
    echo "  Si MySQL corre en otro puerto, ajusta en .env:" >&2
    echo "    DB_HOST=$IP_LOOPBACK" >&2
    echo "    DB_PORT=3306" >&2
  fi
  echo "" >&2
  echo "  El backend NO arranca sin base de datos: Spring Boot necesita MySQL" >&2
  echo "  para resolver el dialecto y el EntityManagerFactory. De ahi el error" >&2
  echo "  'Communications link failure' / 'Conexion rehusada'." >&2
  echo "" >&2
}

# -----------------------------------------------------------------------------
#  COMANDOS
# -----------------------------------------------------------------------------

check() {
  local ok=1
  echo "== TourInvest — check de requisitos (Linux) =="
  for tool in java mvn mysql node; do
    if command -v "$tool" >/dev/null 2>&1; then
      echo "  [OK] $tool  ->  $(command -v "$tool")"
    else
      echo "  [FALTA] $tool"
      ok=0
    fi
  done
  if [ -n "$PY" ]; then
    echo "  [OK] python3  ->  $PY"
    "$PY" --version 2>&1 | sed 's/^/         Python: /'
  else
    echo "  [FALTA] python3  (necesario para servir el sitio en :$PUERTO_WEB)"
    ok=0
  fi
  command -v java >/dev/null 2>&1 && java -version 2>&1 | head -n1 | sed 's/^/         Java: /'

  # Estado real de la base de datos (causa #1 de arranque fallido).
  echo ""
  echo "  -- Base de datos --"
  if puerto_abierto "$DB_HOST" "$DB_PORT"; then
    echo "  [OK] Hay algo escuchando en $DB_HOST:$DB_PORT"
    if command -v mysql >/dev/null 2>&1; then
      # -h/-P fuerzan TCP: un socket local puede existir sin que 3306 responda.
      if mysql_autentica "${DB_PASSWORD:-}"; then
        echo "  [OK] Autenticacion MySQL correcta (usuario '$DB_USER')"
        if MYSQL_PWD="${DB_PASSWORD:-}" mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" \
             -e "USE $DB_NAME; SELECT 1;" >/dev/null 2>&1; then
          echo "  [OK] La base '$DB_NAME' existe"
        else
          echo "  [--] La base '$DB_NAME' aun NO existe. Ejecuta: ./setup.sh seed"
        fi
      else
        echo "  [X] MySQL responde pero rechaza la contrasena de '$DB_USER'."
        echo "      Define DB_PASSWORD en el archivo .env de la raiz."
        ok=0
      fi
    fi
  else
    echo "  [X] Nada escuchando en $DB_HOST:$DB_PORT"
    ok=0
  fi

  # El sitio canonico debe resolver a 127.0.0.1 (ver convencion de hostname).
  echo ""
  echo "  -- Nombre del sitio --"
  verificar_hosts || ok=0

  # El puerto del sitio (80 = URL limpia, sin :80 visible).
  echo ""
  echo "  -- Puerto del sitio --"
  if puerto_abierto "$IP_LOOPBACK" "$PUERTO_WEB"; then
    echo "  [--] Ya hay algo escuchando en el puerto $PUERTO_WEB (puede ser una instancia anterior)."
  elif [ "$PUERTO_WEB" -lt 1024 ] && [ "$(id -u)" -ne 0 ] \
       && ! "$PY" -c "import socket;s=socket.socket();s.bind(('$IP_LOOPBACK',$PUERTO_WEB));s.close()" >/dev/null 2>&1 \
       && ! sudo -n true >/dev/null 2>&1; then
    echo "  [!] El puerto $PUERTO_WEB necesita root. Opciones:"
    echo "      A) sudo setcap 'cap_net_bind_service=+ep' $(readlink -f "$PY")"
    echo "      B) sudo ./setup.sh run    |  C) PUERTO_WEB=8081 en .env"
  else
    echo "  [OK] El sitio se podra servir en $PUERTO_WEB  ($(url_web))"
  fi

  echo ""
  if [ "$ok" -eq 1 ]; then
    echo "  Todo listo. Pasos:"
    echo "    ./setup.sh run              # ARRANCA EL PROYECTO COMPLETO (recomendado)"
    echo "    ./setup.sh seed             # solo si aun no cargaste la BD"
    echo "    API   http://$SITIO:$PUERTO_API"
    echo "    Sitio $(url_web "$PAGINA_INICIO")"
  else
    echo "  Faltan requisitos. Detalle y solucion:"
    diagnosticar_bd
  fi
  return $((ok ? 0 : 1))
}

seed() {
  echo "== Cargando esquema + datos de prueba (query.sql en la RAIZ del proyecto) =="
  if ! command -v mysql >/dev/null 2>&1; then
    echo "  mysql no esta disponible. No se puede sembrar la BD."; exit 1
  fi
  if [ ! -f "$ROOT/query.sql" ]; then
    echo "  No se encontro $ROOT/query.sql"; exit 1
  fi
  if ! puerto_abierto "$DB_HOST" "$DB_PORT"; then
    diagnosticar_bd
    exit 1
  fi

  if [ -n "${DB_PASSWORD:-}" ]; then
    MYSQL_PWD="$DB_PASSWORD" mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" \
      --default-character-set=utf8mb4 < "$ROOT/query.sql"
  else
    # Sin clave en .env: se pide de forma interactiva.
    mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p \
      --default-character-set=utf8mb4 < "$ROOT/query.sql"
  fi

  if [ $? -eq 0 ]; then
    echo ""
    echo "  OK. Base '$DB_NAME' creada y sembrada. Usuarios (clave 123456):"
    echo "$USUARIOS_SEED"
  else
    echo "  X. No se pudo sembrar la base de datos." >&2
    exit 1
  fi
}

# Sin DB_PASSWORD, Spring Boot arranca con la cadena vacia y MySQL responde
# «Access denied ... (using password: NO)». Se pide la contrasena UNA vez y se
# guarda en .env (ignorado por git) para no repetirla en cada arranque.
# Devuelve con error (exit 1) si la contrasena no es valida.
asegurar_password_mysql() {
  [ -n "${DB_PASSWORD:-}" ] && return 0
  command -v mysql >/dev/null 2>&1 || return 0

  local pw=""
  echo "== Configurando acceso a MySQL =="
  read -r -s -p "   Contrasena de '$DB_USER' en $DB_HOST (ENTER si no tiene): " pw
  echo ""
  if [ -n "$pw" ]; then
    # Valida antes de guardar: asi no persistimos una contrasena equivocada.
    if MYSQL_PWD="$pw" mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -e "SELECT 1;" >/dev/null 2>&1; then
      # El puerto/host/usuario tambien deben quedar guardados: si no, Spring
      # Boot conectaria al 3306 por defecto en vez del que se acaba de validar.
      guardar_en_env "DB_PASSWORD" "$pw"
      guardar_en_env "DB_HOST" "$DB_HOST"
      guardar_en_env "DB_PORT" "$DB_PORT"
      guardar_en_env "DB_USER" "$DB_USER"
      guardar_en_env "DB_NAME" "$DB_NAME"
      export DB_PASSWORD="$pw"
      echo "   Contrasena correcta. Guardada en .env (NO versionado)."
    else
      echo "   [X] MySQL rechazo esa contrasena. Vuelve a intentarlo." >&2
      exit 1
    fi
  else
    # Sin contrasena: puede ser valido, pero comprobamos.
    if mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -e "SELECT 1;" >/dev/null 2>&1; then
      echo "   MySQL acepta '$DB_USER' sin contrasena."
    else
      echo "   [X] MySQL no acepta '$DB_USER' sin contrasena. Edita .env y ponla." >&2
      exit 1
    fi
  fi
  return 0
}

# Empaqueta el JAR solo si no existe o si algun .java/.properties es mas
# reciente que el. Deja la ruta del JAR en la variable global JAR.
# POR QUE: `mvn spring-boot:run` tarda ~14s ANTES de que Spring arranque
# (arrancar la JVM de Maven, resolver el POM, compilar y arrancar el plugin).
# Un `java -jar` directo arranca en ~1s.
preparar_jar() {
  local dir="$ROOT/backend-servlets"
  JAR="$dir/target/tourinvest-backend-0.0.1-SNAPSHOT.jar"
  if [ ! -d "$dir" ]; then
    echo "  [X] No existe la carpeta $dir" >&2
    exit 1
  fi
  if [ ! -f "$JAR" ] || [ -n "$(find "$dir/src/main" -newer "$JAR" \
        \( -name '*.java' -o -name '*.properties' \) 2>/dev/null | head -n1)" ]; then
    echo "  Empaquetando el JAR (solo si el codigo cambio)..."
    ( cd "$dir" && mvn -B -q -DskipTests package ) || {
      echo "   [X] Fallo el empaquetado." >&2
      exit 1
    }
  fi
}

run_backend() {
  # Verificamos la BD ANTES de lanzar Maven: fallar aqui con un mensaje claro
  # es mucho mas util que el stack trace de Hibernate.
  if ! puerto_abierto "$DB_HOST" "$DB_PORT"; then
    echo "== TourInvest — no se puede arrancar el backend ==" >&2
    echo "   MySQL no responde en $DB_HOST:$DB_PORT y la API lo necesita para funcionar." >&2
    diagnosticar_bd
    exit 1
  fi
  asegurar_password_mysql

  cd "$ROOT/backend-servlets" || exit 1
  echo "API en http://$SITIO:$PUERTO_API  (Ctrl+C para detener)"
  mvn spring-boot:run
}

# Empaqueta el JAR (si hace falta) y arranca con `java -jar`, saltandose Maven.
run_backend_fast() {
  if ! command -v mvn >/dev/null 2>&1; then
    echo "  [X] Maven no esta instalado. En Linux: sudo apt install maven" >&2
    exit 1
  fi
  if ! puerto_abierto "$DB_HOST" "$DB_PORT"; then
    echo "== TourInvest — no se puede arrancar el backend ==" >&2
    echo "   MySQL no responde en $DB_HOST:$DB_PORT y la API lo necesita para funcionar." >&2
    diagnosticar_bd
    exit 1
  fi
  asegurar_password_mysql
  preparar_jar

  echo "API en http://$SITIO:$PUERTO_API  (Ctrl+C para detener)"
  java -jar "$JAR"
}

# Abre el navegador con la URL indicada. En una sesion SSH sin graficos no
# hay navegador: solo se muestra la URL.
abrir_navegador() {
  local url="$1" cand
  if [ -n "${SSH_CONNECTION:-}" ] && [ -z "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ]; then
    return 0
  fi
  for cand in "${BROWSER:-}" xdg-open sensible-browser gnome-open kde-open \
              x-www-browser google-chrome chromium chromium-browser firefox; do
    [ -n "$cand" ] || continue
    if command -v "$cand" >/dev/null 2>&1; then
      "$cand" "$url" >/dev/null 2>&1 &
      return 0
    fi
  done
  return 0
}

# Sirve ./frontend en :PUERTO_WEB y abre el navegador.
#   run_frontend          -> en primer plano (Ctrl+C la detiene)
#   run_frontend si       -> en segundo plano, para que `run` pueda apagar
#                            los dos servidores con un solo Ctrl+C
run_frontend() {
  local en_segundo_plano="${1:-no}"
  if [ -z "$PY" ]; then
    echo "  [X] python3 no disponible. En Linux: sudo apt install python3" >&2
    exit 1
  fi
  local url
  url="$(url_web "$PAGINA_INICIO")"
  echo "Frontend servido en $url  (Ctrl+C para detener)"
  echo "(necesita que $SITIO apunte a $IP_LOOPBACK en /etc/hosts)"
  resolver_privilegios_web || exit 1
  abrir_navegador "$url"

  # si el puerto es privilegiado, `sudo` se hace exec del interprete: el PID
  # guardado sigue siendo el del servidor, para poder pararlo con Ctrl+C.
  local -a cmd_web=(${PREFIJO_WEB:+"$PREFIJO_WEB"} "$PY" -m http.server "$PUERTO_WEB" --bind "$IP_LOOPBACK")
  if [ "$en_segundo_plano" = "si" ]; then
    ( cd "$ROOT/frontend" && exec "${cmd_web[@]}" ) &
    FRONTEND_PID=$!
    # Esperamos a que el sitio acepte conexiones antes de seguir.
    local i listo=0
    for i in $(seq 1 15); do
      if puerto_abierto "$IP_LOOPBACK" "$PUERTO_WEB"; then listo=1; break; fi
      if ! kill -0 "$FRONTEND_PID" 2>/dev/null; then break; fi
      sleep 1
    done
    if [ "$listo" -eq 1 ]; then
      echo "  [OK] Sitio listo en $url"
    else
      echo "  [X] El sitio no respondio en el puerto $PUERTO_WEB." >&2
    fi
    wait "$FRONTEND_PID"     # espera hasta que llegue una senal
  else
    ( cd "$ROOT/frontend" && exec "${cmd_web[@]}" )
  fi
}

# ARRANQUE COMPLETO DEL PROYECTO: API en :8080 + sitio en el puerto 80.
# Ambos servidores quedan en segundo plano: el backend escribiendo su log en
# logs/backend.log y el sitio sirviendo en el puerto $PUERTO_WEB. Con un solo
# Ctrl+C (o al cerrar la terminal) se apagan los DOS.
detener_servidores() {
  trap - EXIT INT TERM HUP
  if [ -n "${FRONTEND_PID:-}" ] && kill -0 "$FRONTEND_PID" 2>/dev/null; then
    kill "$FRONTEND_PID" 2>/dev/null
    wait "$FRONTEND_PID" 2>/dev/null
  fi
  if [ -n "${BACKEND_PID:-}" ] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo ""
    echo "  Deteniendo el backend (pid $BACKEND_PID)..."
    kill "$BACKEND_PID" 2>/dev/null
    wait "$BACKEND_PID" 2>/dev/null
  fi
}

run_todo() {
  echo "== TourInvest — arranque completo del proyecto =="
  if ! command -v mvn >/dev/null 2>&1; then
    echo "  [X] Maven no esta instalado. En Linux: sudo apt install maven" >&2
    exit 1
  fi
  if [ -z "$PY" ]; then
    echo "  [X] python3 no disponible. En Linux: sudo apt install python3" >&2
    exit 1
  fi
  if ! puerto_abierto "$DB_HOST" "$DB_PORT"; then
    echo "   MySQL no responde en $DB_HOST:$DB_PORT y la API lo necesita para funcionar." >&2
    diagnosticar_bd
    exit 1
  fi
  asegurar_password_mysql
  preparar_jar

  mkdir -p "$LOG_DIR"
  local log="$LOG_DIR/backend.log"

  echo "  Backend  : http://$SITIO:$PUERTO_API   (log en logs/backend.log)"
  ( cd "$ROOT/backend-servlets" && exec java -jar "$JAR" ) > "$log" 2>&1 &
  BACKEND_PID=$!
  # INT = Ctrl+C, TERM = kill, HUP = se cerro la terminal, EXIT = salida normal.
  trap detener_servidores EXIT INT TERM HUP

  # Esperamos a que la API acepte conexiones antes de abrir el navegador.
  echo "  Levantando la API (hasta 60 s)..."
  local i listo=0
  for i in $(seq 1 60); do
    if puerto_abierto "$IP_LOOPBACK" "$PUERTO_API"; then listo=1; break; fi
    if ! kill -0 "$BACKEND_PID" 2>/dev/null; then break; fi
    sleep 1
  done
  if [ "$listo" -ne 1 ]; then
    echo "  [X] La API no respondio en el puerto $PUERTO_API. Ultimas lineas del log:" >&2
    tail -n 20 "$log" >&2
    exit 1
  fi
  echo "  [OK] API lista."
  echo ""

  # El sitio tambien en segundo plano: asi un solo Ctrl+C apaga API + sitio.
  run_frontend si
}

helpMsg() {
  cat <<EOF
Uso: ./setup.sh <comando>

  check             Verifica java/mvn/mysql/node/python Y la conexion real a MySQL.
                    Es el primer comando: si falla, dice exactamente que falta.
  seed              Carga query.sql (raiz) en la base '$DB_NAME'.
  run-backend       Compila y ejecuta Spring Boot en :$PUERTO_API.
  run-backend-fast  Empaqueta el JAR y arranca con 'java -jar'.
                    MUCHO mas rapido (~14s menos) porque se salta Maven.
                    Reempaqueta solo si el codigo cambio.
  run-frontend      Sirve ./frontend en el puerto $PUERTO_WEB y abre el navegador.
  run               ARRANCA EL PROYECTO COMPLETO: API :$PUERTO_API + sitio :$PUERTO_WEB.
                    El backend queda en segundo plano (log en logs/backend.log) y
                    el sitio en esta terminal. Ctrl+C detiene los dos.
  help              Muestra esta ayuda.

Arranque habitual en Linux:
  1) sudo systemctl start mysql     # el servidor MySQL debe estar arriba
  2) ./setup.sh check               # diagnostico
  3) ./setup.sh run                 # API + sitio, y abre el navegador

Direccion web:  $(url_web "$PAGINA_INICIO")      <- sin :$PUERTO_WEB, se ve como dominio
API:            http://$SITIO:$PUERTO_API

Usuarios de prueba (clave 123456): kike@tourinvest.com (Administrador),
marlen@tourinvest.com (Analista), juan@tourinvest.com (Inversionista).

Configuracion opcional (archivo .env en la raiz del proyecto):
  DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, JWT_SECRET
  Si .env no existe, se crea solo la primera vez que tecleas la contrasena.

Convencion de hostname:
  direccion real  $IP_LOOPBACK       (nunca 'localhost')
  nombre visible  $SITIO  (mapearlo en /etc/hosts)
EOF
}

case "${1:-help}" in
  check)            check ;;
  seed)             seed ;;
  run-backend)      run_backend ;;
  run-backend-fast) run_backend_fast ;;
  run-frontend)     run_frontend ;;
  run)              run_todo ;;
  help|-h|--help)   helpMsg ;;
  *) echo "Comando desconocido: $1"; echo ""; helpMsg; exit 2 ;;
esac
