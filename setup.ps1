<#
.SYNOPSIS
    TourInvest — setup.ps1   (WINDOWS)
.DESCRIPTION
    Script de arranque del proyecto: diagnostico, base de datos y servidores.
    Detecta automaticamente las rutas reales de mysql.exe y mvn.cmd (incluido el
    Maven que trae la extension Oracle Java de VS Code).

.REQUISITOS (todo se instala en Windows)
    JDK 17+     https://adoptium.net  (marca "Windows" y ".zip")
    Maven       https://maven.apache.org/download.cgi  (Binary zip)
    MySQL       https://dev.mysql.com/downloads/installer/  (o MariaDB)
    Python      https://www.python.org/downloads/windows/  (marca "Add to PATH")

.COMANDOS
    .\setup.ps1 check             # verifica java/mvn/mysql/node/python Y la conexion a la BD
    .\setup.ps1 seed              # carga query.sql (raiz) en la BD 'tourinvest'
    .\setup.ps1 run-backend       # compila y arranca Spring Boot (API en :8080)
    .\setup.ps1 run-backend-fast  # empaqueta el JAR y arranca con 'java -jar' (mas rapido)
    .\setup.ps1 run-frontend      # sirve .\frontend en el puerto 80 y abre el navegador
    .\setup.ps1 run               # ARRANCA EL PROYECTO COMPLETO (API + sitio)
    .\setup.ps1 help

.URL LIMPIA (sin :80)
    El sitio se sirve en el puerto 80, asi que en el navegador se ve
    http://www.tourinvest.com  (parece un dominio de verdad, sin ":80").
    Si el 80 esta ocupado (IIS/Apache), cambia PUERTO_WEB=8081 en .env.

.EJEMPLO
    Si PowerShell bloquea los scripts de esta carpeta, ejecuta:
    powershell -NoProfile -ExecutionPolicy Bypass -File .\setup.ps1 check
    (tambien funciona con doble clic: .\setup.cmd run)

.CONVENCION DE HOSTNAME (no improvisar)
    * Direccion real: SIEMPRE 127.0.0.1. Nunca 'localhost': en IPv6 resuelve a
      ::1 y rompe JDBC/CORS de forma intermitente.
    * Nombre visible: www.tourinvest.com, mapeado a 127.0.0.1 en el archivo de
      hosts de Windows.
#>
param(
    [Parameter(Position = 0)][string]$Action = "help",
    [string]$MySqlExe = "",
    [string]$MvnCmd = ""
)

$script:Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$script:Self = $MyInvocation.MyCommand.Path
$script:CheckOk = $true

# --- Direcciones y puertos del proyecto --------------------------------------
$script:Sitio = "www.tourinvest.com"         # nombre visible en el navegador
$script:IpLoopback = "127.0.0.1"            # direccion real (loopback IPv4)
$script:PuertoApi = 8080                     # API Spring Boot
$script:PaginaInicio = ""   # punto de entrada de la aplicacion

# --- Carga opcional del archivo .env (si existe) ----------------------------
# Permite definir DB_PASSWORD, JWT_SECRET, etc. SIN escribirlos en
# application.properties (que va versionado en git).
$envFile = Join-Path $script:Root ".env"
if (Test-Path $envFile) {
    foreach ($line in Get-Content $envFile) {
        $trimmed = $line.Trim()
        if ($trimmed -and -not $trimmed.StartsWith("#") -and $trimmed.Contains("=")) {
            $idx = $trimmed.IndexOf("=")
            $key = $trimmed.Substring(0, $idx).Trim()
            $val = $trimmed.Substring($idx + 1).Trim().Trim('"').Trim("'")
            [Environment]::SetEnvironmentVariable($key, $val, "Process")
        }
    }
    Write-Host "  (configuracion leida desde .env)" -ForegroundColor DarkGray
}

$script:DbHost = if ($env:DB_HOST) { $env:DB_HOST } else { $script:IpLoopback }
$script:DbPort = if ($env:DB_PORT) { $env:DB_PORT } else { "3306" }
$script:DbName = if ($env:DB_NAME) { $env:DB_NAME } else { "tourinvest" }
$script:DbUser = if ($env:DB_USER) { $env:DB_USER } else { "root" }
$script:EnvFile = $envFile

# El sitio se sirve en el puerto 80 para que la URL sea limpia y parezca de
# dominio real: http://www.tourinvest.com  (sin :80 que ver).
# Cambialo en .env (PUERTO_WEB=8081) si el 80 esta ocupado (IIS/Apache).
$script:PuertoWeb = if ($env:PUERTO_WEB) { $env:PUERTO_WEB } else { "80" }

# Credenciales de prueba sembradas por query.sql (clave 123456).
# Estos son los roles REALES segun query.sql (id_rol 1/2/3):
#   Administrador = kike@, Analista = marlen@, Inversionista = juan@
$UsuariosSeed = @"
    kike@tourinvest.com     Administrador
    marlen@tourinvest.com   Analista
    juan@tourinvest.com     Inversionista
"@

# -----------------------------------------------------------------------------
#  RESOLUCION DE BINARIOS
# -----------------------------------------------------------------------------

function Resolve-Mysql([string]$Override) {
    if ($Override -and (Test-Path $Override)) { return $Override }
    foreach ($v in @("9.6", "9.4", "9.2", "8.4", "8.0")) {
        $p = "C:\Program Files\MySQL\MySQL Server $v\bin\mysql.exe"
        if (Test-Path $p) { return $p }
    }
    $maria = Get-Item "C:\Program Files\MariaDB *\bin\mysql.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($maria) { return $maria.FullName }
    $cmd = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if (-not $cmd) { $cmd = Get-Command mysql -ErrorAction SilentlyContinue }
    if ($cmd) { return $cmd.Source }
    return $null
}

function Resolve-Mvn([string]$Override) {
    if ($Override -and (Test-Path $Override)) { return $Override }
    $cmd = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if (-not $cmd) { $cmd = Get-Command mvn -ErrorAction SilentlyContinue }
    if ($cmd) { return $cmd.Source }
    # Fallback: Maven incluido con la extension Oracle Java de VS Code
    $extRoot = Join-Path $env:USERPROFILE ".vscode\extensions"
    if (Test-Path $extRoot) {
        foreach ($d in Get-ChildItem $extRoot -Directory -ErrorAction SilentlyContinue) {
            $p = Join-Path $d.FullName "nbcode\java\maven\bin\mvn.cmd"
            if (Test-Path $p) { return $p }
        }
    }
    return $null
}

# Un interprete solo sirve si realmente arranca: un .venv viejo o roto
# no debe dejar al sitio sin servir.
function Test-PythonEjecutable([string]$Ruta) {
    if (-not $Ruta -or -not (Test-Path $Ruta)) { return $false }
    try {
        & $Ruta --version 2>&1 | Out-Null
        return ($LASTEXITCODE -eq 0)
    } catch { return $false }
}

function Resolve-Python {
    $venvPy = Join-Path $script:Root ".venv\Scripts\python.exe"
    if (Test-PythonEjecutable $venvPy) { return $venvPy }   # preferimos el .venv del repositorio
    foreach ($nombre in @("python.exe", "python", "py.exe")) {
        $cmd = Get-Command $nombre -ErrorAction SilentlyContinue
        if ($cmd -and (Test-PythonEjecutable $cmd.Source)) { return $cmd.Source }
    }
    return $null
}

$script:Mysql   = Resolve-Mysql $MySqlExe
$script:Mvn     = Resolve-Mvn $MvnCmd
$script:Python  = Resolve-Python
$script:Jar     = "target\tourinvest-backend-0.0.1-SNAPSHOT.jar"
# PID del lanzador (`run`) que inicio esta API; 0 si se lanzo a mano.
$script:LanzadorId = if ($env:TOURINVEST_LANZADOR) { [int]$env:TOURINVEST_LANZADOR } else { 0 }

function Test-RunningService {
    if (-not (Get-Command Get-Service -ErrorAction SilentlyContinue)) { return @() }
    Get-Service -ErrorAction SilentlyContinue | Where-Object { $_.DisplayName -match 'mysql|maria' }
}

# Titulo de la ventana de PowerShell (evita confusiones con varias abiertas).
function Set-TituloVentana([string]$Texto) {
    try { $Host.UI.RawUI.WindowTitle = $Texto } catch { }
}

# URL del sitio. En el puerto 80 NO se escribe ":80": se ve como un dominio
# de verdad (http://www.tourinvest.com).
function Get-UrlWeb([string]$Ruta = "") {
    # Equivalente a url_web() de setup.sh: puerto 80 => URL limpia sin ":80".
    # Normaliza la barra entre el dominio y la pagina: sin slash el navegador
    # pediria "www.tourinvest.comindex.html" (DNS inexistente) y romperia el
    # login con "No fue posible conectar con el servidor".
    $base = "http://$($script:Sitio)"
    if ([int]$script:PuertoWeb -ne 80) { $base += ":$($script:PuertoWeb)" }
    $p = "$Ruta".Trim().TrimStart("/")
    if ([string]::IsNullOrWhiteSpace($p)) { return $base }
    return "$base/$p"
}

# Indica si el puerto esta libre ahora mismo. En Windows los puertos <1024 no
# estan restringidos, pero IIS/Apache suelen tener el 80 reservado (y entonces
# python falla con "Access is denied" o "Address already in use").
function Test-PuertoLibre([int]$Port) {
    try {
        $listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Parse($script:IpLoopback), $Port)
        $listener.Start()
        $listener.Stop()
        return $true
    } catch { return $false }
}

# Si el puerto del sitio esta ocupado, explica como liberarlo o como usar otro.
function Show-PuertoWebOcupado {
    Write-Host "  [X] El puerto $($script:PuertoWeb) ya esta ocupado por otro servicio." -ForegroundColor Red
    Write-Host "      Libera el puerto:" -ForegroundColor Red
    Write-Host "        net stop w3svc        # IIS" -ForegroundColor Red
    Write-Host "        taskkill /IM httpd.exe /F   # Apache" -ForegroundColor Red
    Write-Host "      O usa otro puerto en .env:  PUERTO_WEB=8081" -ForegroundColor Red
    Write-Host "      (y entonces la URL llevara :8081 al final)." -ForegroundColor Red
}

# Verifica que el nombre visible resuelva a 127.0.0.1 ANTES de arrancar.
# Si www.tourinvest.com resuelve a ::1 (IPv6) o a otra IP, el sitio carga pero
# el fetch a la API falla con "No fue posible conectar con el servidor".
# Convencion documentada en setup.sh (SITIO/IP_LOOPBACK): nombre visible vs
# direccion real. Solo diagnostica; no cambia ningun archivo del sistema.
function Test-ResolucionSitio {
    try {
        $ips = [System.Net.Dns]::GetHostAddresses($script:Sitio) | ForEach-Object { $_.IPAddressToString }
    } catch {
        $ips = @()
    }
    if ($ips -contains $script:IpLoopback) { return $true }
    Write-Host "  [X] '$($script:Sitio)' no resuelve a $($script:IpLoopback)." -ForegroundColor Red
    if ($ips.Count -gt 0 -and -not [string]::IsNullOrWhiteSpace("$ips")) {
        Write-Host "      Resuelve a: $($ips -join ', ')" -ForegroundColor Red
    } else {
        Write-Host "      No resuelve a ninguna direccion (falta la entrada en hosts)." -ForegroundColor Red
    }
    Write-Host "      El sitio cargara, pero el login fallara con 'No fue posible" -ForegroundColor Red
    Write-Host "      conectar con el servidor' porque el navegador no llega a la API." -ForegroundColor Red
    Write-Host "      Agrega esta linea al archivo de hosts de Windows" -ForegroundColor Yellow
    Write-Host "      (C:\Windows\System32\drivers\etc\hosts, con Notepad como Administrador):" -ForegroundColor Yellow
    Write-Host "        $($script:IpLoopback)   $($script:Sitio)" -ForegroundColor Yellow
    return $false
}

# Comprueba si el puerto TCP responde, sin depender de mysqladmin.
function Test-PuertoAbierto {
    param([string]$Host_, [int]$Port)
    try {
        $cliente = New-Object System.Net.Sockets.TcpClient
        $tarea = $cliente.ConnectAsync($Host_, $Port)
        if ($tarea.Wait(2000) -and $cliente.Connected) { $cliente.Close(); return $true }
        $cliente.Close()
    } catch { }
    return $false
}

# El sitio canonico DEBE apuntar a 127.0.0.1. Si no, el navegador se ira a
# Internet (hoy ese dominio resuelve a un IP publico) y la app no respondera.
function Test-SitioEnHosts {
    $ip = $null
    try {
        $entradas = [System.Net.Dns]::GetHostAddresses($script:Sitio)
        foreach ($e in $entradas) { if ($e.AddressFamily -eq 'InterNetwork') { $ip = $e.IPAddressToString; break } }
    } catch { }
    if (-not $ip) {
        Write-Host "  [--] '$($script:Sitio)' no resuelve en este equipo."
        Write-Host "      Anadelo al archivo de hosts de Windows:"
        Write-Host "          $($script:IpLoopback) $($script:Sitio)"
        return $true   # no es bloqueante: la API igual funciona por 127.0.0.1
    }
    if ($ip -eq $script:IpLoopback) {
        Write-Host "  [OK] $($script:Sitio) -> $ip"
        return $true
    }
    Write-Host "  [X] $($script:Sitio) resuelve a $ip, deberia ser $($script:IpLoopback)." -ForegroundColor Red
    Write-Host "      Anade esta linea al archivo de hosts de Windows:" -ForegroundColor Red
    Write-Host "          $($script:IpLoopback) $($script:Sitio)" -ForegroundColor Red
    Write-Host "      PowerShell como ADMINISTRADOR:" -ForegroundColor Red
    Write-Host "          Add-Content -Path `"$env:SystemRoot\System32\drivers\etc\hosts`" -Value `"$($script:IpLoopback) $($script:Sitio)`"" -ForegroundColor Red
    Write-Host "      O abre el Bloc de notas como administrador sobre ese mismo archivo." -ForegroundColor Red
    return $false
}

# -----------------------------------------------------------------------------
#  BASE DE DATOS
# -----------------------------------------------------------------------------

# Explica por que falla la conexion: es el error mas comun de arranque.
function Show-DiagnosticoBD {
    Write-Host ""
    Write-Host "  [X] No hay conexion con MySQL en $($script:DbHost):$($script:DbPort)" -ForegroundColor Red
    Write-Host ""
    if ($script:Mysql) {
        Write-Host "  Causa mas probable: el SERVIDOR MySQL no esta corriendo." -ForegroundColor Yellow
        Write-Host "    O en PowerShell (como administrador):" -ForegroundColor Yellow
        Write-Host "        Start-Service *mysql*" -ForegroundColor Yellow
        Write-Host "    O abre services.msc y pulsa Iniciar en el servicio MySQL." -ForegroundColor Yellow
        Write-Host ""
        Write-Host "  Si MySQL corre en otro puerto, ajustalo en .env:  DB_HOST / DB_PORT" -ForegroundColor Yellow
    } else {
        Write-Host "  Causa probable: falta el CLIENTE mysql." -ForegroundColor Yellow
        Write-Host "    Reinstala MySQL Server (incluye mysql.exe) o pasa -MySqlExe <ruta>." -ForegroundColor Yellow
    }
    Write-Host ""
    Write-Host "  El backend NO arranca sin base de datos: Spring Boot necesita MySQL" -ForegroundColor Red
    Write-Host "  para resolver el dialecto y el EntityManagerFactory. De ahi el error" -ForegroundColor Red
    Write-Host "  'Communications link failure' / 'Conexion rehusada'." -ForegroundColor Red
    Write-Host ""
}

# Comprobacion real de credenciales contra MySQL. Reutilizada por check/seed/run-*.
# Equivalente a mysql_autentica() de setup.sh: con clave usa MYSQL_PWD (nunca
# --password=, que falla con caracteres especiales y expone la clave en la
# linea de comandos); sin clave no se pasa ningun flag de password.
function Test-MysqlAuth {
    param([string]$Password, [string]$Query = "SELECT 1;")
    if ($Password) {
        $env:MYSQL_PWD = $Password
        try {
            & $script:Mysql -h $script:DbHost -P $script:DbPort -u $script:DbUser -e $Query 2>&1 | Out-Null
            return ($LASTEXITCODE -eq 0)
        } finally {
            Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
        }
    } else {
        & $script:Mysql -h $script:DbHost -P $script:DbPort -u $script:DbUser -e $Query 2>&1 | Out-Null
        return ($LASTEXITCODE -eq 0)
    }
}

# Crea el archivo .env con los valores por defecto si todavia no existe.
# .env esta en .gitignore: la contrasena nunca se versiona.
function Initialize-EnvFile {
    if (Test-Path $script:EnvFile) { return }
    @(
        "# TourInvest - configuracion local (creada por setup.ps1)"
        "DB_HOST=$($script:DbHost)"
        "DB_PORT=$($script:DbPort)"
        "DB_NAME=$($script:DbName)"
        "DB_USER=$($script:DbUser)"
        "DB_PASSWORD="
    ) | Set-Content -Path $script:EnvFile -Encoding UTF8
}

# Guarda un valor en el archivo .env de la raiz.
function Set-InEnvFile {
    param([string]$Clave, [string]$Valor)
    Initialize-EnvFile
    $lineas = @(Get-Content $script:EnvFile)
    $nuevas = @()
    $encontrado = $false
    foreach ($l in $lineas) {
        if ($l -match "^\s*$([regex]::Escape($Clave))\s*=") {
            $nuevas += "$Clave=$Valor"
            $encontrado = $true
        } else {
            $nuevas += $l
        }
    }
    if (-not $encontrado) { $nuevas += "$Clave=$Valor" }
    Set-Content -Path $script:EnvFile -Value $nuevas -Encoding UTF8
}

# Sin DB_PASSWORD, Spring Boot arranca con la cadena vacia y MySQL responde
# «Access denied ... (using password: NO)». Se pide la contrasena UNA vez y se
# guarda en .env (ignorado por git) para no repetirla en cada arranque.
function Initialize-DbPassword {
    if ($env:DB_PASSWORD) { return }
    if (-not $script:Mysql) { return }

    Write-Host "== Configurando acceso a MySQL ==" -ForegroundColor Cyan
    $seguro = Read-Host "   Escribe la contrasena de '$($script:DbUser)' en $($script:DbHost) (ENTER si no tiene)" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro)
    $plano = [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)

    if ($plano) {
        # Valida antes de guardar: asi no persistimos una contrasena equivocada.
        if (-not (Test-MysqlAuth -Password $plano)) {
            Write-Host "   [X] MySQL rechazo esa contrasena. Vuelve a intentarlo." -ForegroundColor Red
            exit 1
        }
        # El host/puerto/usuario/BD tambien deben quedar guardados: si no,
        # Spring Boot conectaria al 3306 por defecto en vez del validado.
        Set-InEnvFile "DB_PASSWORD" $plano
        Set-InEnvFile "DB_HOST"     $script:DbHost
        Set-InEnvFile "DB_PORT"     $script:DbPort
        Set-InEnvFile "DB_USER"     $script:DbUser
        Set-InEnvFile "DB_NAME"     $script:DbName
        $env:DB_PASSWORD = $plano
        Write-Host "   Contrasena correcta. Guardada en .env (NO versionado)." -ForegroundColor Green
    } else {
        # Sin contrasena: puede ser valido, pero comprobamos.
        if (-not (Test-MysqlAuth -Password "")) {
            Write-Host "   [X] MySQL no acepta '$($script:DbUser)' sin contrasena. Edita .env y ponla." -ForegroundColor Red
            exit 1
        }
        Write-Host "   MySQL acepta '$($script:DbUser)' sin contrasena." -ForegroundColor Green
    }
}

# Empaqueta el JAR solo si no existe o si algun .java/.properties es mas
# reciente que el, y deja la ruta completa en $script:JarRuta.
# POR QUE: `mvn spring-boot:run` tarda ~14s ANTES de que Spring arranque
# (arrancar la JVM de Maven, resolver el POM, compilar y arrancar el plugin).
# Un `java -jar` directo arranca en ~1s.
function Build-JarIfNeeded {
    $dir = Join-Path $script:Root "backend-servlets"
    $jar = Join-Path $dir $script:Jar
    if (-not (Test-Path $dir)) {
        Write-Host "  [X] No existe la carpeta $dir" -ForegroundColor Red
        exit 1
    }
    $hayCambios = $false
    if (-not (Test-Path $jar)) {
        $hayCambios = $true
    } else {
        $jarTime = (Get-Item $jar).LastWriteTime
        $reciente = Get-ChildItem -Path (Join-Path $dir "src\main") -Recurse -Include *.java,*.properties -ErrorAction SilentlyContinue |
                    Where-Object { $_.LastWriteTime -gt $jarTime } | Select-Object -First 1
        $hayCambios = ($null -ne $reciente)
    }
    if ($hayCambios) {
        Write-Host "  Empaquetando el JAR (solo si el codigo cambio)..." -ForegroundColor Cyan
        Push-Location $dir
        try {
            & $script:Mvn -B -q -DskipTests package
            if ($LASTEXITCODE -ne 0) { Write-Host "   [X] Fallo el empaquetado." -ForegroundColor Red; exit 1 }
        } finally {
            Pop-Location
        }
    }
    $script:JarRuta = $jar
}

# -----------------------------------------------------------------------------
#  COMANDOS
# -----------------------------------------------------------------------------

# Estado real de la base de datos (causa #1 de arranque fallido).
function Show-EstadoBD {
    Write-Host ""
    Write-Host "  -- Base de datos --"
    if (Test-PuertoAbierto -Host_ $script:DbHost -Port ([int]$script:DbPort)) {
        Write-Host "  [OK] Hay algo escuchando en $($script:DbHost):$($script:DbPort)"
        $servicios = Test-RunningService
        foreach ($s in $servicios) { Write-Host "         servicio '$($s.Name)': $($s.Status)" }
        $pw = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "" }
        if (Test-MysqlAuth -Password $pw) {
            Write-Host "  [OK] Autenticacion MySQL correcta (usuario '$($script:DbUser)')"
            if (Test-MysqlAuth -Password $pw -Query "USE $($script:DbName); SELECT 1;") {
                Write-Host "  [OK] La base '$($script:DbName)' existe"
            } else {
                Write-Host "  [--] La base '$($script:DbName)' aun NO existe. Ejecuta: .\setup.ps1 seed"
            }
        } else {
            Write-Host "  [X] MySQL responde pero rechaza la contrasena de '$($script:DbUser)'."
            Write-Host "      Define DB_PASSWORD en el archivo .env de la raiz."
            $script:CheckOk = $false
        }
    } else {
        Write-Host "  [X] Nada escuchando en $($script:DbHost):$($script:DbPort)"
        $script:CheckOk = $false
    }
}

function Show-Check {
    Set-TituloVentana "TourInvest - check"
    Write-Host "== TourInvest - check de requisitos (Windows) ==" -ForegroundColor Cyan
    $script:CheckOk = $true

    $javaCmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if (-not $javaCmd) { $javaCmd = Get-Command java -ErrorAction SilentlyContinue }
    if ($javaCmd) {
        $v = (& java -version 2>&1 | Select-Object -First 1)
        Write-Host "  [OK] java   -> $($javaCmd.Source)"
        Write-Host "         $v"
    } else { Write-Host "  [FALTA] java" -ForegroundColor Red; $script:CheckOk = $false }

    if ($script:Mvn) { Write-Host "  [OK] mvn    -> $($script:Mvn)" }
    else { Write-Host "  [FALTA] mvn (instala Maven: https://maven.apache.org/download.cgi)" -ForegroundColor Red; $script:CheckOk = $false }

    if ($script:Mysql) {
        Write-Host "  [OK] mysql  -> $($script:Mysql)"
        Test-RunningService | ForEach-Object { Write-Host "         servicio '$($_.Name)': $($_.Status)" }
    } else { Write-Host "  [FALTA] mysql (reinstala MySQL Server o pasa -MySqlExe <ruta>)" -ForegroundColor Red; $script:CheckOk = $false }

    $nodeCmd = Get-Command node.exe -ErrorAction SilentlyContinue
    if (-not $nodeCmd) { $nodeCmd = Get-Command node -ErrorAction SilentlyContinue }
    if ($nodeCmd) { Write-Host "  [OK] node   -> $($nodeCmd.Source)" }
    else { Write-Host "  [--] node (opcional, solo validar sintaxis JS)" }

    if ($script:Python) {
        $pyv = try { & $script:Python --version 2>&1 } catch { "no se pudo ejecutar" }
        Write-Host "  [OK] python -> $($script:Python)"
        Write-Host "         $pyv"
    } else {
        Write-Host "  [FALTA] python (necesario para servir el sitio en :$($script:PuertoWeb))" -ForegroundColor Red
        $script:CheckOk = $false
    }

    # Estado real de la base de datos (causa #1 de arranque fallido).
    if ($script:Mysql) { Show-EstadoBD }

    # El sitio canonico debe resolver a 127.0.0.1 (ver convencion de hostname).
    Write-Host ""
    Write-Host "  -- Nombre del sitio --"
    if (-not (Test-SitioEnHosts)) { $script:CheckOk = $false }

    # El puerto del sitio (80 = URL limpia, sin :80 visible).
    Write-Host ""
    Write-Host "  -- Puerto del sitio --"
    if (Test-PuertoAbierto -Host_ $script:IpLoopback -Port ([int]$script:PuertoWeb)) {
        Write-Host "  [--] Ya hay algo escuchando en el puerto $($script:PuertoWeb) (puede ser una instancia anterior)."
    } elseif (Test-PuertoLibre ([int]$script:PuertoWeb)) {
        Write-Host "  [OK] El sitio se podra servir en el puerto $($script:PuertoWeb)  ($(Get-UrlWeb))"
    } else {
        Show-PuertoWebOcupado
        $script:CheckOk = $false
    }

    if ($script:CheckOk) {
        Write-Host ""
        Write-Host "  Todo listo. Pasos:" -ForegroundColor Green
        Write-Host "    .\setup.ps1 run             # ARRANCA EL PROYECTO COMPLETO (recomendado)"
        Write-Host "    .\setup.ps1 seed            # solo si aun no cargaste la BD"
        Write-Host "    API   http://$($script:Sitio):$($script:PuertoApi)"
        Write-Host "    Sitio $(Get-UrlWeb $script:PaginaInicio)"
    } else {
        Write-Host ""
        Write-Host "  Faltan requisitos. Detalle y solucion:" -ForegroundColor Yellow
        Show-DiagnosticoBD
    }
}

function Invoke-Seed {
    Set-TituloVentana "TourInvest - seed"
    Write-Host "== Cargando esquema + datos de prueba (query.sql, raiz del proyecto) ==" -ForegroundColor Cyan
    if (-not $script:Mysql) {
        Write-Host "  mysql.exe no encontrado. Reinstala MySQL/MariaDB o pasa -MySqlExe <ruta>." -ForegroundColor Red
        exit 1
    }
    $sql = Join-Path $script:Root "query.sql"
    if (-not (Test-Path $sql)) {
        Write-Host "  No se encontro $sql" -ForegroundColor Red
        exit 1
    }
    if (-not (Test-PuertoAbierto -Host_ $script:DbHost -Port ([int]$script:DbPort))) {
        Show-DiagnosticoBD
        exit 1
    }
    # Con la contrasena de .env si existe; si no, se pide de forma interactiva.
    # Igual que setup.sh: con clave se usa MYSQL_PWD, sin clave se pide con -p.
    if ($env:DB_PASSWORD) {
        $env:MYSQL_PWD = $env:DB_PASSWORD
        try {
            Get-Content $sql | & $script:Mysql --default-character-set=utf8mb4 -h $script:DbHost -P $script:DbPort -u $script:DbUser
        } finally {
            Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
        }
    } else {
        Get-Content $sql | & $script:Mysql --default-character-set=utf8mb4 -h $script:DbHost -P $script:DbPort -u $script:DbUser -p
    }
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "  OK. Base '$($script:DbName)' creada y sembrada. Usuarios (clave 123456):" -ForegroundColor Green
        Write-Host $UsuariosSeed -ForegroundColor Green
    } else {
        Write-Host "  X. No se pudo sembrar la base de datos." -ForegroundColor Red
        exit 1
    }
}

function Invoke-RunBackend {
    Set-TituloVentana "TourInvest - API :$($script:PuertoApi)"
    if (-not $script:Mvn) {
        Write-Host "  mvn.cmd no encontrado. Instala Maven o pasa -MvnCmd <ruta>." -ForegroundColor Red
        exit 1
    }
    # Verificamos la BD ANTES de lanzar Maven: fallar aqui con un mensaje claro
    # es mucho mas util que el stack trace de Hibernate.
    if (-not (Test-PuertoAbierto -Host_ $script:DbHost -Port ([int]$script:DbPort))) {
        Write-Host "== TourInvest - no se puede arrancar el backend ==" -ForegroundColor Red
        Write-Host "   MySQL no responde en $($script:DbHost):$($script:DbPort) y la API lo necesita." -ForegroundColor Red
        Show-DiagnosticoBD
        exit 1
    }
    Initialize-DbPassword

    # La contrasena NUNCA se escribe en application.properties (esta versionado en
    # git). Se guarda en el archivo .env local, que esta en .gitignore.
    Push-Location (Join-Path $script:Root "backend-servlets")
    try {
        Write-Host "API escuchando en http://$($script:Sitio):$($script:PuertoApi)  (Ctrl+C para detener)" -ForegroundColor Cyan
        & $script:Mvn spring-boot:run
    } finally {
        Pop-Location   # restaura la carpeta del usuario al salir
    }
}

# Empaqueta el JAR (si hace falta) y arranca con `java -jar`, saltandose Maven.
function Invoke-RunBackendFast {
    Set-TituloVentana "TourInvest - API :$($script:PuertoApi)"
    if (-not $script:Mvn) {
        Write-Host "  mvn.cmd no encontrado. Instala Maven o pasa -MvnCmd <ruta>." -ForegroundColor Red
        exit 1
    }
    if (-not (Test-PuertoAbierto -Host_ $script:DbHost -Port ([int]$script:DbPort))) {
        Write-Host "== TourInvest - no se puede arrancar el backend ==" -ForegroundColor Red
        Write-Host "   MySQL no responde en $($script:DbHost):$($script:DbPort) y la API lo necesita." -ForegroundColor Red
        Show-DiagnosticoBD
        exit 1
    }
    Initialize-DbPassword
    Build-JarIfNeeded

    Write-Host "API escuchando en http://$($script:Sitio):$($script:PuertoApi)  (Ctrl+C para detener)" -ForegroundColor Cyan
    # java se lanza como proceso hijo y se vigila: asi se detiene SIEMPRE con
    # esta ventana, aunque alguien cierre la consola.
    $proc = Start-Process -FilePath "java" -ArgumentList @("-jar", $script:JarRuta) -NoNewWindow -PassThru
    try {
        while (-not $proc.HasExited) {
            # Si esta API la lanzo `run`, se vigila al lanzador: en cuanto esa
            # ventana se cierra (Ctrl+C o la X), la API se apaga sola y no
            # queda ningun java huerfano ocupando el puerto 8080.
            if ($script:LanzadorId -gt 0 -and -not (Get-Process -Id $script:LanzadorId -ErrorAction SilentlyContinue)) {
                Write-Host "El lanzador se cerro: la API se detiene." -ForegroundColor Yellow
                break
            }
            Start-Sleep -Seconds 2
        }
    } finally {
        if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
    }
}

# Sirve .\frontend en :PUERTO_WEB y abre el navegador.
function Invoke-RunFrontend {
    Set-TituloVentana "TourInvest - Sitio :$($script:PuertoWeb)"
    if (-not $script:Python) {
        Write-Host "  python no disponible. Instala Python 3 (https://www.python.org/downloads/windows/) para servir el sitio." -ForegroundColor Red
        exit 1
    }
    $url = Get-UrlWeb $script:PaginaInicio
    Write-Host "Frontend servido en $url  (Ctrl+C para detener)" -ForegroundColor Cyan
    Write-Host "(necesita que $($script:Sitio) apunte a $($script:IpLoopback) en el archivo de hosts de Windows)"
    if (-not (Test-PuertoAbierto -Host_ $script:IpLoopback -Port ([int]$script:PuertoWeb)) `
        -and -not (Test-PuertoLibre ([int]$script:PuertoWeb))) {
        Show-PuertoWebOcupado
        exit 1
    }
    Start-Process $url   # abre el navegador automaticamente

    # El servidor se lanza como proceso hijo y se vigila, para que el sitio
    # quede apagado SIEMPRE que termina esta ventana.
    $proc = Start-Process -FilePath $script:Python -NoNewWindow -PassThru `
                -ArgumentList @("-m", "http.server", "$($script:PuertoWeb)", "--bind", $script:IpLoopback) `
                -WorkingDirectory (Join-Path $script:Root "frontend")
    try {
        while (-not $proc.HasExited) {
            if ($script:LanzadorId -gt 0 -and -not (Get-Process -Id $script:LanzadorId -ErrorAction SilentlyContinue)) { break }
            Start-Sleep -Seconds 1
        }
    } finally {
        if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
    }
}

# ARRANQUE COMPLETO DEL PROYECTO: API en :8080 + sitio en el puerto 80.
# Los dos servidores arrancan como procesos supervisados (logs\backend.log y
# logs\frontend.log) y el navegador se abre solo. Con un Ctrl+C (o al cerrar
# esta ventana) se apagan los DOS.
function Start-All {
    Set-TituloVentana "TourInvest - Proyecto completo"
    Write-Host "== TourInvest - arranque completo del proyecto ==" -ForegroundColor Cyan
    if (-not $script:Mvn) {
        Write-Host "  mvn.cmd no encontrado. Instala Maven o pasa -MvnCmd <ruta>." -ForegroundColor Red
        exit 1
    }
    if (-not $script:Python) {
        Write-Host "  python no disponible. Instala Python 3 (https://www.python.org/downloads/windows/) para servir el sitio." -ForegroundColor Red
        exit 1
    }
    if (-not (Test-PuertoAbierto -Host_ $script:DbHost -Port ([int]$script:DbPort))) {
        Write-Host "   MySQL no responde en $($script:DbHost):$($script:DbPort) y la API lo necesita para funcionar." -ForegroundColor Red
        Show-DiagnosticoBD
        exit 1
    }
    Initialize-DbPassword
    Build-JarIfNeeded

    # Igual que setup.sh antes de lanzar: si el nombre visible no resuelve al
    # loopback, el navegador nunca llegara a la API y el login mostrara
    # "No fue posible conectar con el servidor". Solo avisa, no bloquea.
    Test-ResolucionSitio | Out-Null

    $logDir = Join-Path $script:Root "logs"
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $logApi  = Join-Path $logDir "backend.log"
    $errApi  = Join-Path $logDir "backend-error.log"
    $logWeb  = Join-Path $logDir "frontend.log"
    $errWeb  = Join-Path $logDir "frontend-error.log"
    Remove-Item $logApi, $errApi, $logWeb, $errWeb -ErrorAction SilentlyContinue

    # -NoNewWindow: los dos servidores comparten esta consola, asi que Ctrl+C o
    # cerrar la ventana los detiene a los dos. El PID del lanzador viaja en la
    # variable de entorno para que cada uno pueda apagarse solo si esta ventana
    # desaparece de golpe.
    $env:TOURINVEST_LANZADOR = $PID
    $lineaApi = "-NoProfile -ExecutionPolicy Bypass -File `"$($script:Self)`" run-backend-fast"
    $procApi = Start-Process -FilePath "powershell.exe" -ArgumentList $lineaApi -NoNewWindow -PassThru `
                        -RedirectStandardOutput $logApi -RedirectStandardError $errApi
    $procWeb = $null
    try {
        Write-Host "  Backend  : http://$($script:Sitio):$($script:PuertoApi)   (log en logs\backend.log)"
        Write-Host "  Levantando la API (hasta 60 s)..."
        $listo = $false
        for ($i = 1; $i -le 60; $i++) {
            if (Test-PuertoAbierto -Host_ $script:IpLoopback -Port $script:PuertoApi) { $listo = $true; break }
            if ($procApi.HasExited) { break }
            Start-Sleep -Seconds 1
        }
        if (-not $listo) {
            Write-Host "  [X] La API no respondio en el puerto $($script:PuertoApi). Ultimas lineas de logs\backend.log:" -ForegroundColor Red
            if (Test-Path $logApi) { Get-Content $logApi -Tail 15 | ForEach-Object { Write-Host "      $_" -ForegroundColor Red } }
            if (Test-Path $errApi) { Get-Content $errApi -Tail 15 | ForEach-Object { Write-Host "      $_" -ForegroundColor Red } }
            Write-Host "      Si el log esta vacio, ejecuta en otra ventana: .\setup.ps1 run-backend-fast" -ForegroundColor Red
            Write-Host "      (ahi se ve el error real de Spring/Hibernate en directo)." -ForegroundColor Red
            Write-Host "      Para ver el error completo ejecuta: .\setup.ps1 run-backend" -ForegroundColor Red
            exit 1
        }
        Write-Host "  [OK] API lista." -ForegroundColor Green
        Write-Host ""

        $lineaWeb = "-NoProfile -ExecutionPolicy Bypass -File `"$($script:Self)`" run-frontend"
        $procWeb = Start-Process -FilePath "powershell.exe" -ArgumentList $lineaWeb -NoNewWindow -PassThru `
                            -RedirectStandardOutput $logWeb -RedirectStandardError $errWeb
        Write-Host "  Sitio    : $(Get-UrlWeb $script:PaginaInicio)   (log en logs\frontend.log)"

        # Esperamos a que el sitio acepte conexiones y abrimos el navegador.
        for ($i = 1; $i -le 30; $i++) {
            if (Test-PuertoAbierto -Host_ $script:IpLoopback -Port $script:PuertoWeb) { break }
            if ($procWeb.HasExited) { break }
            Start-Sleep -Seconds 1
        }
        if (Test-PuertoAbierto -Host_ $script:IpLoopback -Port $script:PuertoWeb) {
            Start-Process (Get-UrlWeb $script:PaginaInicio)
            Write-Host "  [OK] Sitio listo. Navegador abierto." -ForegroundColor Green
        } else {
            Write-Host "  [X] El sitio no respondio en el puerto $($script:PuertoWeb). Mira logs\frontend.log" -ForegroundColor Red
        }
        Write-Host ""
        Write-Host "  Ctrl+C (o cierra esta ventana) para detener API y sitio." -ForegroundColor Cyan
        Write-Host ""

        # Aqui se queda la ventana hasta que el usuario la cierre.
        while (-not $procApi.HasExited -or -not $procWeb.HasExited) {
            Start-Sleep -Seconds 2
        }
    } finally {
        Write-Host ""
        Write-Host "  Deteniendo API y sitio..."
        if ($procWeb -and -not $procWeb.HasExited) { Stop-Process -Id $procWeb.Id -Force -ErrorAction SilentlyContinue }
        if (-not $procApi.HasExited) { Stop-Process -Id $procApi.Id -Force -ErrorAction SilentlyContinue }
    }
}

function Show-Help {
    Write-Host "Uso: .\setup.ps1 <comando>" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "  check             Verifica java/mvn/mysql/node/python Y la conexion real a MySQL."
    Write-Host "                    Es el primer comando: si falla, dice exactamente que falta."
    Write-Host "  seed              Carga query.sql (raiz) en la base '$($script:DbName)'."
    Write-Host "  run-backend       Compila y ejecuta Spring Boot en :$($script:PuertoApi)."
    Write-Host "  run-backend-fast  Empaqueta el JAR y arranca con 'java -jar'."
    Write-Host "                    MUCHO mas rapido (~14s menos) porque se salta Maven."
    Write-Host "                    Reempaqueta solo si el codigo cambio."
    Write-Host "  run-frontend      Sirve .\frontend en el puerto $($script:PuertoWeb) y abre el navegador."
    Write-Host "  run               ARRANCA EL PROYECTO COMPLETO: API :$($script:PuertoApi) + sitio :$($script:PuertoWeb)."
    Write-Host "                    Levanta los dos servidores y abre el navegador."
    Write-Host "                    Logs en logs\backend.log y logs\frontend.log."
    Write-Host "                    Ctrl+C (o cerrar la ventana) detiene los dos."
    Write-Host "  help              Muestra esta ayuda."
    Write-Host ""
    Write-Host "Arranque habitual en Windows:" -ForegroundColor Cyan
    Write-Host "  1) net start MySQL        (o services.msc y pulsar Iniciar)"
    Write-Host "  2) .\setup.ps1 check      (diagnostico)"
    Write-Host "  3) .\setup.ps1 run        (API + sitio, y abre el navegador)"
    Write-Host "     Tambien funciona con doble clic:  .\setup.cmd run"
    Write-Host ""
    Write-Host "Direccion web:  $(Get-UrlWeb $script:PaginaInicio)      <- sin :$($script:PuertoWeb), se ve como dominio"
    Write-Host "API:            http://$($script:Sitio):$($script:PuertoApi)"
    Write-Host ""
    Write-Host "Usuarios de prueba (clave 123456): kike@tourinvest.com (Administrador),"
    Write-Host "marlen@tourinvest.com (Analista), juan@tourinvest.com (Inversionista)."
    Write-Host ""
    Write-Host "Configuracion opcional (archivo .env en la raiz del proyecto):" -ForegroundColor Cyan
    Write-Host "  DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, JWT_SECRET"
    Write-Host "  Si .env no existe, se crea solo la primera vez que tecleas la contrasena."
    Write-Host ""
    Write-Host "Convencion de hostname:" -ForegroundColor Cyan
    Write-Host "  direccion real  $($script:IpLoopback)        (nunca 'localhost')"
    Write-Host "  nombre visible  $($script:Sitio)  (mapearlo en el archivo de hosts de Windows)"
    Write-Host ""
    Write-Host "Extras: -MySqlExe <ruta> y -MvnCmd <ruta> para forzar binarios concretos."
    Write-Host "Si PowerShell bloquea los scripts: powershell -NoProfile -ExecutionPolicy Bypass -File .\setup.ps1 check"
}

switch ($Action.ToLowerInvariant()) {
    "check"            { Show-Check;        exit ([int](-not $script:CheckOk)) }
    "seed"             { Invoke-Seed }
    "run-backend"      { Invoke-RunBackend }
    "run-backend-fast" { Invoke-RunBackendFast }
    "run-frontend"     { Invoke-RunFrontend }
    "run"              { Start-All }
    "help"             { Show-Help }
    "-h"               { Show-Help }
    "--help"           { Show-Help }
    default            { Write-Host "Comando desconocido: $Action" -ForegroundColor Yellow; Show-Help; exit 2 }
}