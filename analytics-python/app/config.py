"""Configuración de la aplicación cargada a partir de variables de entorno."""

import os


class Config:
    """Configuración base."""

    DATABASE_URL: str = os.getenv("DATABASE_URL", "sqlite:///wattwise.db")
    PORT: int = int(os.getenv("PORT", "5000"))
    DAYS_DEFAULT: int = int(os.getenv("DAYS_DEFAULT", "30"))
    SQLALCHEMY_TRACK_MODIFICATIONS: bool = False


class TestConfig(Config):
    """Configuración de pruebas — SQLite en memoria."""

    TESTING: bool = True
    DATABASE_URL: str = "sqlite:///:memory:"


class DevelopmentConfig(Config):
    """Desarrollo local — SQLite basado en archivos."""

    DATABASE_URL: str = os.getenv("DATABASE_URL", "sqlite:///wattwise.db")


class ProductionConfig(Config):
    """Producción — SQL Server mediante pyodbc.

    Ejemplo de DATABASE_URL (se define por variable de entorno; nunca se
    confirman credenciales):
        mssql+pyodbc://user:******@host:1433/dbname?driver=ODBC+Driver+17+for+SQL+Server
    """

    pass


config_by_name = {
    "development": DevelopmentConfig,
    "testing": TestConfig,
    "production": ProductionConfig,
}
