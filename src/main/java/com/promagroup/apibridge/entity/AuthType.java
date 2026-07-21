package com.promagroup.apibridge.entity;

/** Estrategia de autenticacao usada pelo API Client ao chamar a API externa. */
public enum AuthType {
    NONE,
    BEARER,
    API_KEY,
    BASIC,
    OAUTH2
}
