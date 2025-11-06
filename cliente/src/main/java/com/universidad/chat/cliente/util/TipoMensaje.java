package com.universidad.chat.cliente.util;

/**
 * Enumeración de tipos de mensajes del protocolo propietario.
 */
public enum TipoMensaje {
    REGISTRO(0x01),
    AUTENTICACION(0x02),
    MENSAJE_TEXTO(0x03),
    MENSAJE_AUDIO(0x04),
    SOLICITUD_CANAL(0x05),
    RESPUESTA_CANAL(0x06),
    MENSAJE_CANAL(0x07),
    SOLICITUD_LISTA(0x08),
    ENVIO_ARCHIVO(0x09),
    CIERRE_SESION(0x0A),
    NOTIFICACION(0x0B);

    private final byte codigo;

    TipoMensaje(int codigo) {
        this.codigo = (byte) codigo;
    }

    public byte getCodigo() {
        return codigo;
    }

    public static TipoMensaje fromCodigo(byte codigo) {
        for (TipoMensaje tipo : values()) {
            if (tipo.codigo == codigo) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Código de mensaje no válido: " + codigo);
    }
}
