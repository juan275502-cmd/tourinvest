// -----------------------------------------------------------------------------
// TourInvestUI.Configuracion — legales + selector de tema (compartido por los 3
// roles). El texto legal vive SOLO aquí para no triplicarlo en los 3 HTML; cada
// vista "Configuración" ofrece botones que abren este modal.
// -----------------------------------------------------------------------------
window.TourInvestUI = window.TourInvestUI || {};

window.TourInvestUI.Configuracion = (function () {
  const CONTENIDO = {
    tratamiento: {
      titulo: "Política de tratamiento de datos personales",
      cuerpo: `
        <p>TourInvest S.A.S., en adelante «la Empresa», tratará los datos personales que usted facilita al registrarse y usar la plataforma, de conformidad con la Ley 1581 de 2012, el Decreto 1377 de 2013 y las demás normas vigentes.</p>
        <h4>1. Finalidad del tratamiento</h4>
        <p>Los datos (nombre, apellido, cédula, correo, fecha de nacimiento y los que se deriven de su uso) se recogen con la finalidad de: crear y administrar su cuenta de acceso, identificarlo dentro de los roles (Inversionista, Analista o Administrador), operar portafolios, alertas, reportes y recomendaciones, y dar cumplimiento a obligaciones legales.</p>
        <h4>2. Derechos del titular</h4>
        <p>Usted puede conocer, actualizar, rectificar y solicitar la supresión de sus datos, así como revocar la autorización otorgada, escribiendo a contacto@tourinvest.co. La solicitud se atenderá en los plazos que fija la ley.</p>
        <h4>3. Encargados y transferencia</h4>
        <p>Los datos se almacenan en la infraestructura del proyecto y no se comparten con terceros para fines distintos a los aquí descritos, salvo obligación legal.</p>
        <h4>4. Vigencia</h4>
        <p>La presente política rige desde su publicación y aplica a todos los usuarios de la plataforma.</p>`
    },
    privacidad: {
      titulo: "Política de privacidad",
      cuerpo: `
        <p>Esta política explica cómo TourInvest protege su información al usar la plataforma.</p>
        <h4>1. Información que recopilamos</h4>
        <p>Datos de registro y perfil, datos de uso de la plataforma (portafolio, alertas y reportes que usted crea) y datos técnicos mínimos necesarios para la sesión (token de acceso).</p>
        <h4>2. Cómo la protegemos</h4>
        <p>Las contraseñas se guardan cifradas (BCrypt), la comunicación con el backend se realiza sobre HTTP y el acceso a las operaciones está autenticado mediante JSON Web Token, por lo que cada usuario solo puede consultar y modificar sus propios datos.</p>
        <h4>3. Cookies y almacenamiento local</h4>
        <p>La sesión se mantiene en el almacenamiento de la pestaña del navegador (sessionStorage) y se borra al cerrar sesión. La preferencia de tema (claro/oscuro) se guarda en el almacenamiento local (localStorage).</p>
        <h4>4. Contacto</h4>
        <p>Para dudas sobre privacidad escriba a contacto@tourinvest.co.</p>`
    },
    terminos: {
      titulo: "Términos y condiciones de uso",
      cuerpo: `
        <p>El acceso a TourInvest implica la aceptación de los siguientes términos.</p>
        <h4>1. Uso de la plataforma</h4>
        <p>La plataforma es un sistema de análisis e inversión para el sector turístico colombiano. Usted se compromete a usarla de forma lícita y a no intentar vulnerar su seguridad.</p>
        <h4>2. Roles y responsabilidades</h4>
        <p>Cada usuario accede según el rol asignado por el Administrador: el Inversionista gestiona su portafolio y alertas; el Analista genera indicadores, reportes y recomendaciones; el Administrador gestiona usuarios, empresas y auditorías. El rol y el estado de la cuenta los asigna y modifica únicamente el Administrador.</p>
        <h4>3. Naturaleza de la información</h4>
        <p>Los precios, indicadores y recomendaciones son informativos y de demostración; no constituyen asesoría financiera ni garantía de rentabilidad. La decisión de inversión es exclusiva del usuario.</p>
        <h4>4. Datos de Colombia</h4>
        <p>La información geográfica y turística (departamentos, regiones y atracciones) proviene de la API pública api-colombia.com y se ofrece con fines descriptivos.</p>
        <h4>5. Modificaciones</h4>
        <p>Estos términos pueden actualizarse; el uso continuado de la plataforma implica su aceptación.</p>`
    }
  };

  function montarModal() {
    if (document.getElementById("modal-legal")) return;
    document.body.insertAdjacentHTML("beforeend", `
      <div class="modal" id="modal-legal" aria-hidden="true" role="dialog">
        <div class="modal__backdrop" data-close-modal onclick="TourInvestUI.Configuracion.cerrar()"></div>
        <div class="modal__card">
          <div class="modal__header">
            <h3 id="modal-legal-titulo">Documento</h3>
            <button class="modal__close" type="button" onclick="TourInvestUI.Configuracion.cerrar()" aria-label="Cerrar">✕</button>
          </div>
          <div class="modal__body">
            <div class="legal-texto" id="modal-legal-cuerpo"></div>
          </div>
        </div>
      </div>`);
  }

  function abrir(clave) {
    const doc = CONTENIDO[clave];
    if (!doc) return;
    montarModal();
    document.getElementById("modal-legal-titulo").textContent = doc.titulo;
    document.getElementById("modal-legal-cuerpo").innerHTML = doc.cuerpo;
    const modal = document.getElementById("modal-legal");
    modal.classList.add("modal--open");
    modal.setAttribute("aria-hidden", "false");
  }

  function cerrar() {
    const modal = document.getElementById("modal-legal");
    if (!modal) return;
    modal.classList.remove("modal--open");
    modal.setAttribute("aria-hidden", "true");
  }

  return { abrir, cerrar, claves: Object.keys(CONTENIDO) };
})();
