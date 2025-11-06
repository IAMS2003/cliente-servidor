package com.universidad.chat.servidor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vosk.Model;
import org.vosk.Recognizer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Servicio de transcripción de audio usando VOSK.
 * Carga el modelo de español y transcribe audio WAV a texto.
 */
public class TranscripcionService {
    private static final Logger log = LoggerFactory.getLogger(TranscripcionService.class);
    private static final String MODEL_PATH = "vosk-models/vosk-model-small-es-0.42";
    
    private Model model;
    private final Gson gson = new Gson();
    private boolean modeloCargado = false;

    public TranscripcionService() {
        cargarModelo();
    }

    private void cargarModelo() {
        try {
            Path modelPath = Paths.get(MODEL_PATH);
            if (!Files.exists(modelPath)) {
                log.warn("Modelo VOSK no encontrado en: {}. Transcripción deshabilitada.", MODEL_PATH);
                log.warn("Descarga el modelo desde: https://alphacephei.com/vosk/models");
                log.warn("Descomprime vosk-model-small-es-0.42 en la carpeta vosk-models/");
                modeloCargado = false;
                return;
            }
            
            log.info("Cargando modelo VOSK desde: {}", MODEL_PATH);
            model = new Model(MODEL_PATH);
            modeloCargado = true;
            log.info("Modelo VOSK cargado exitosamente");
            
        } catch (Exception e) {
            log.error("Error al cargar modelo VOSK", e);
            modeloCargado = false;
        }
    }

    /**
     * Transcribe audio WAV a texto en español.
     * 
     * @param audioWav bytes del audio en formato WAV (16kHz, mono, 16-bit)
     * @return texto transcrito o cadena vacía si falla
     */
    public String transcribir(byte[] audioWav) {
        if (!modeloCargado || model == null) {
            log.debug("Modelo no cargado, transcripción omitida");
            return "";
        }

        try (Recognizer recognizer = new Recognizer(model, 16000)) {
            // Saltar el encabezado WAV (44 bytes estándar)
            int offset = 44;
            if (audioWav.length < offset) {
                log.warn("Audio demasiado corto para procesar");
                return "";
            }

            // Procesar audio en chunks
            int chunkSize = 4096;
            int remaining = audioWav.length - offset;
            
            while (remaining > 0) {
                int size = Math.min(chunkSize, remaining);
                byte[] chunk = new byte[size];
                System.arraycopy(audioWav, offset, chunk, 0, size);
                recognizer.acceptWaveForm(chunk, size);
                offset += size;
                remaining -= size;
            }

            // Obtener resultado final
            String resultJson = recognizer.getFinalResult();
            JsonObject result = gson.fromJson(resultJson, JsonObject.class);
            
            if (result.has("text")) {
                String transcripcion = result.get("text").getAsString().trim();
                log.info("Transcripción: {}", transcripcion.isEmpty() ? "(vacía)" : transcripcion);
                return transcripcion;
            }
            
            return "";
            
        } catch (Exception e) {
            log.error("Error al transcribir audio", e);
            return "";
        }
    }

    /**
     * Verifica si el servicio está listo para transcribir.
     */
    public boolean estaListo() {
        return modeloCargado && model != null;
    }

    /**
     * Libera recursos del modelo.
     */
    public void cerrar() {
        if (model != null) {
            try {
                model.close();
                log.info("Modelo VOSK cerrado");
            } catch (Exception e) {
                log.error("Error al cerrar modelo VOSK", e);
            }
        }
    }
}
