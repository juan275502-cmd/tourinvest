from flask import Blueprint, request, jsonify
from features.dashboard.data.repository_impl import DashboardRepositoryImpl

dashboard_bp = Blueprint("dashboard", __name__, url_prefix="/api/dashboard")
repo = DashboardRepositoryImpl()


@dashboard_bp.route("/usuarios", methods=["GET"])
def listar_usuarios():
    return jsonify({"ok": True, "usuarios": repo.listar_usuarios()})


@dashboard_bp.route("/usuarios", methods=["POST"])
def crear_usuario():
    """RF13 - Gestionar usuarios"""
    data = request.get_json(silent=True) or {}
    requeridos = ["nombre", "apellido", "cedula", "correo", "password", "rol"]
    faltantes = [c for c in requeridos if not data.get(c)]
    if faltantes:
        return jsonify({"ok": False, "mensaje": f"Campos faltantes: {', '.join(faltantes)}"}), 400
    try:
        usuario = repo.crear_usuario(data)
    except ValueError as e:
        return jsonify({"ok": False, "mensaje": str(e)}), 409
    return jsonify({"ok": True, "usuario": usuario}), 201


@dashboard_bp.route("/usuarios/<int:usuario_id>/estado", methods=["PATCH"])
def cambiar_estado_usuario(usuario_id):
    """RF13 - Suspender / activar usuario"""
    data = request.get_json(silent=True) or {}
    try:
        usuario = repo.cambiar_estado(usuario_id, data.get("estado"))
    except ValueError as e:
        return jsonify({"ok": False, "mensaje": str(e)}), 400
    if usuario is None:
        return jsonify({"ok": False, "mensaje": "Usuario no encontrado."}), 404
    return jsonify({"ok": True, "usuario": usuario})


@dashboard_bp.route("/roles", methods=["GET"])
def listar_roles():
    return jsonify({"ok": True, "roles": repo.listar_roles()})


@dashboard_bp.route("/auditorias", methods=["GET"])
def listar_auditorias():
    return jsonify({"ok": True, "auditorias": repo.listar_auditorias()})