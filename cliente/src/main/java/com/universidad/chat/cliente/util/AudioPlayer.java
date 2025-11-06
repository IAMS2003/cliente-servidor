package com.universidad.chat.cliente.util;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Reproductor de audio para archivos WAV.
 * Soporta operaciones de play, pause y stop.
 */
public class AudioPlayer {
    private Clip clip;
    private boolean isPaused = false;
    private long pausePosition = 0;
    private Runnable onPlaybackComplete;

    /**
     * Carga un archivo de audio desde bytes.
     * 
     * @param audioData Los bytes del audio en formato WAV
     * @throws IOException Si ocurre un error al leer los datos
     * @throws UnsupportedAudioFileException Si el formato de audio no es soportado
     * @throws LineUnavailableException Si la línea de audio no está disponible
     */
    public void loadAudio(byte[] audioData) throws IOException, UnsupportedAudioFileException, LineUnavailableException {
        // Cerrar clip anterior si existe
        if (clip != null && clip.isOpen()) {
            clip.close();
        }

        // Crear stream de audio desde los bytes
        ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bais);

        // Crear y abrir el clip
        clip = AudioSystem.getClip();
        clip.open(audioInputStream);
        
        // Listener para detectar fin de reproducción (no confundir con pausa)
        clip.addLineListener(event -> {
            if (event.getType() == LineEvent.Type.STOP) {
                // Si no estamos en pausa y llegamos (o muy cerca) al final, considerar como completado
                long pos = clip.getMicrosecondPosition();
                long len = clip.getMicrosecondLength();
                boolean finished = !isPaused && len > 0 && pos >= Math.max(0, len - 1000); // tolerancia 1ms
                if (finished) {
                    try {
                        clip.stop();
                        clip.setFramePosition(0);
                    } catch (Exception ignored) {}
                    isPaused = false;
                    pausePosition = 0;
                    if (onPlaybackComplete != null) {
                        onPlaybackComplete.run();
                    }
                }
            }
        });
        
        isPaused = false;
        pausePosition = 0;
    }

    /**
     * Reproduce el audio desde el inicio o desde donde fue pausado.
     */
    public void play() {
        if (clip == null) {
            return;
        }

        if (isPaused) {
            // Continuar desde donde fue pausado
            clip.setMicrosecondPosition(pausePosition);
            clip.start();
            isPaused = false;
        } else {
            // Reproducir desde el inicio
            clip.setFramePosition(0);
            clip.start();
        }
    }

    /**
     * Pausa la reproducción del audio.
     */
    public void pause() {
        if (clip != null && clip.isRunning()) {
            pausePosition = clip.getMicrosecondPosition();
            clip.stop();
            isPaused = true;
        }
    }

    /**
     * Detiene la reproducción y reinicia la posición al inicio.
     */
    public void stop() {
        if (clip != null) {
            clip.stop();
            clip.setFramePosition(0);
            isPaused = false;
            pausePosition = 0;
        }
    }

    /**
     * Verifica si el audio está actualmente reproduciéndose.
     * 
     * @return true si está reproduciéndose, false en caso contrario
     */
    public boolean isPlaying() {
        return clip != null && clip.isRunning();
    }

    /**
     * Verifica si el audio está pausado.
     * 
     * @return true si está pausado, false en caso contrario
     */
    public boolean isPaused() {
        return isPaused;
    }

    /**
     * Libera los recursos del reproductor.
     */
    public void close() {
        if (clip != null) {
            clip.close();
            clip = null;
        }
        isPaused = false;
        pausePosition = 0;
    }

    /**
     * Registra un callback para cuando la reproducción finaliza naturalmente.
     * @param listener Runnable ejecutado al terminar el audio
     */
    public void setOnPlaybackComplete(Runnable listener) {
        this.onPlaybackComplete = listener;
    }

    /**
     * Obtiene la duración total del audio en microsegundos.
     * 
     * @return La duración en microsegundos, o 0 si no hay audio cargado
     */
    public long getDuration() {
        if (clip != null) {
            return clip.getMicrosecondLength();
        }
        return 0;
    }

    /**
     * Obtiene la posición actual de reproducción en microsegundos.
     * 
     * @return La posición actual en microsegundos
     */
    public long getPosition() {
        if (clip != null) {
            return clip.getMicrosecondPosition();
        }
        return 0;
    }
}
