package com.universidad.chat.servidor.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Clase que representa un mensaje del protocolo propietario.
 * Estructura: [Versión(1)][Tipo(1)][Longitud(4)][IdUsuario(4)][Cuerpo(n)]
 */
public class Mensaje {
    private static final byte VERSION_PROTOCOLO = 0x01;
    
    private byte version;
    private TipoMensaje tipo;
    private int longitudCuerpo;
    private int idUsuario;
    private byte[] cuerpo;

    public Mensaje(TipoMensaje tipo, int idUsuario, byte[] cuerpo) {
        this.version = VERSION_PROTOCOLO;
        this.tipo = tipo;
        this.idUsuario = idUsuario;
        this.cuerpo = cuerpo != null ? cuerpo : new byte[0];
        this.longitudCuerpo = this.cuerpo.length;
    }

    public Mensaje(TipoMensaje tipo, int idUsuario, String cuerpoTexto) {
        this(tipo, idUsuario, cuerpoTexto.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Serializa el mensaje a bytes según el protocolo.
     */
    public byte[] serializar() {
        ByteBuffer buffer = ByteBuffer.allocate(10 + longitudCuerpo);
        buffer.put(version);
        buffer.put(tipo.getCodigo());
        buffer.putInt(longitudCuerpo);
        buffer.putInt(idUsuario);
        buffer.put(cuerpo);
        return buffer.array();
    }

    /**
     * Deserializa un mensaje desde bytes.
     */
    public static Mensaje deserializar(byte[] datos) {
        if (datos.length < 10) {
            throw new IllegalArgumentException("Datos insuficientes para deserializar");
        }

        ByteBuffer buffer = ByteBuffer.wrap(datos);
        byte version = buffer.get();
        byte tipoCodigo = buffer.get();
        int longitudCuerpo = buffer.getInt();
        int idUsuario = buffer.getInt();

        if (datos.length < 10 + longitudCuerpo) {
            throw new IllegalArgumentException("Longitud de cuerpo inconsistente");
        }

        byte[] cuerpo = new byte[longitudCuerpo];
        buffer.get(cuerpo);

        Mensaje mensaje = new Mensaje(TipoMensaje.fromCodigo(tipoCodigo), idUsuario, cuerpo);
        mensaje.version = version;
        return mensaje;
    }

    // Getters
    public byte getVersion() {
        return version;
    }

    public TipoMensaje getTipo() {
        return tipo;
    }

    public int getLongitudCuerpo() {
        return longitudCuerpo;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public byte[] getCuerpo() {
        return cuerpo;
    }

    public String getCuerpoTexto() {
        return new String(cuerpo, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "Mensaje{" +
                "version=" + version +
                ", tipo=" + tipo +
                ", longitudCuerpo=" + longitudCuerpo +
                ", idUsuario=" + idUsuario +
                ", cuerpo=" + getCuerpoTexto() +
                '}';
    }
}
