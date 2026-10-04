"""Autenticación y autorización para la API de analítica."""

import os
from functools import wraps

from flask import jsonify, request, current_app


class AuthError(Exception):
    """Excepción para errores de autenticación."""

    def __init__(self, message: str, status_code: int = 401):
        super().__init__(message)
        self.message = message
        self.status_code = status_code


def get_api_key() -> str | None:
    """Obtiene la API key configurada desde la variable de entorno."""
    return current_app.config.get("ANALYTICS_API_KEY") or os.getenv("ANALYTICS_API_KEY")


def validate_api_key(api_key: str | None) -> bool:
    """Valida la API key proporcionada contra la configurada."""
    expected = get_api_key()
    if not expected:
        # Si no hay clave configurada, no se requiere autenticación (modo desarrollo)
        return True
    return api_key == expected


def require_auth(f):
    """Decorador que exige autenticación vía header X-API-Key.

    - Si ANALYTICS_API_KEY no está configurada, permite el acceso (modo desarrollo).
    - Si está configurada, exige el header X-API-Key con el valor correcto.
    - Responde 401 si falta o es inválida.
    """

    @wraps(f)
    def decorated(*args, **kwargs):
        # Health endpoints no requieren autenticación
        if request.endpoint and request.endpoint.startswith("health."):
            return f(*args, **kwargs)

        api_key = request.headers.get("X-API-Key")
        if not validate_api_key(api_key):
            return (
                jsonify(
                    {
                        "error": {
                            "code": "UNAUTHORIZED",
                            "message": "Invalid or missing API key",
                        }
                    }
                ),
                401,
            )
        return f(*args, **kwargs)

    return decorated


def init_auth(app):
    """Inicializa la configuración de autenticación en la app."""
    # Leer la API key desde variable de entorno
    api_key = os.getenv("ANALYTICS_API_KEY")
    if api_key:
        app.config["ANALYTICS_API_KEY"] = api_key