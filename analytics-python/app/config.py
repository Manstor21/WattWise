"""Application configuration loaded from environment variables."""

import os


class Config:
    """Base configuration."""

    DATABASE_URL: str = os.getenv("DATABASE_URL", "sqlite:///wattwise.db")
    PORT: int = int(os.getenv("PORT", "5000"))
    DAYS_DEFAULT: int = int(os.getenv("DAYS_DEFAULT", "30"))
    SQLALCHEMY_TRACK_MODIFICATIONS: bool = False


class TestConfig(Config):
    """Testing configuration — in-memory SQLite."""

    TESTING: bool = True
    DATABASE_URL: str = "sqlite:///:memory:"


class DevelopmentConfig(Config):
    """Local development — file-based SQLite."""

    DATABASE_URL: str = os.getenv("DATABASE_URL", "sqlite:///wattwise.db")


class ProductionConfig(Config):
    """Production — SQL Server via pyodbc.

    DATABASE_URL example (set via env, never commit credentials):
        mssql+pyodbc://user:password@host:1433/dbname?driver=ODBC+Driver+17+for+SQL+Server
    """

    pass


config_by_name = {
    "development": DevelopmentConfig,
    "testing": TestConfig,
    "production": ProductionConfig,
}
