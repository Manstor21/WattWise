package com.wattwise.model.enums;

/**
 * Plataforma del dispositivo que posee un token push. Actualmente solo la app
 * Android del repo registra tokens; IOS y WEB se reservan para futuros clientes.
 */
public enum Platform {
    ANDROID,
    IOS,
    WEB
}