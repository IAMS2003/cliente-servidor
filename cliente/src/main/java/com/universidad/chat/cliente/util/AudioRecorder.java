package com.universidad.chat.cliente.util;

import javax.sound.sampled.*;
import java.io.*;

/**
 * Utilidad para grabar audio desde el micrófono.
 */
public class AudioRecorder {
    private static final AudioFormat FORMAT = new AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        16000, // Sample rate
        16,    // Sample size in bits
        1,     // Channels (mono)
        2,     // Frame size
        16000, // Frame rate
        false  // Big endian
    );
    
    private TargetDataLine microphone;
    private ByteArrayOutputStream audioStream;
    private Thread recordingThread;
    private volatile boolean recording = false;

    /**
     * Inicia la grabación de audio.
     */
    public void startRecording() throws LineUnavailableException {
        if (recording) {
            throw new IllegalStateException("Ya se está grabando");
        }

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("El formato de audio no es soportado");
        }

        microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(FORMAT);
        microphone.start();

        audioStream = new ByteArrayOutputStream();
        recording = true;

        recordingThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            while (recording) {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    audioStream.write(buffer, 0, bytesRead);
                }
            }
        }, "audio-recorder");
        
        recordingThread.start();
    }

    /**
     * Detiene la grabación y retorna los bytes del audio en formato WAV.
     */
    public byte[] stopRecording() throws IOException {
        if (!recording) {
            throw new IllegalStateException("No se está grabando");
        }

        recording = false;

        try {
            if (recordingThread != null) {
                recordingThread.join(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }

        byte[] audioData = audioStream.toByteArray();
        audioStream.close();

        // Convertir a formato WAV
        return convertToWav(audioData);
    }

    /**
     * Convierte los datos PCM raw a formato WAV.
     */
    private byte[] convertToWav(byte[] audioData) throws IOException {
        ByteArrayOutputStream wavStream = new ByteArrayOutputStream();
        
        // Escribir encabezado WAV
        writeWavHeader(wavStream, audioData.length);
        
        // Escribir datos de audio
        wavStream.write(audioData);
        
        return wavStream.toByteArray();
    }

    /**
     * Escribe el encabezado WAV.
     */
    private void writeWavHeader(ByteArrayOutputStream out, int audioDataLength) throws IOException {
        int totalDataLen = audioDataLength + 36;
        int byteRate = (int) (FORMAT.getSampleRate() * FORMAT.getChannels() * FORMAT.getSampleSizeInBits() / 8);
        
        // RIFF header
        out.write("RIFF".getBytes());
        writeInt(out, totalDataLen);
        out.write("WAVE".getBytes());
        
        // fmt chunk
        out.write("fmt ".getBytes());
        writeInt(out, 16); // Subchunk1Size for PCM
        writeShort(out, (short) 1); // AudioFormat (1 = PCM)
        writeShort(out, (short) FORMAT.getChannels());
        writeInt(out, (int) FORMAT.getSampleRate());
        writeInt(out, byteRate);
        writeShort(out, (short) (FORMAT.getChannels() * FORMAT.getSampleSizeInBits() / 8)); // BlockAlign
        writeShort(out, (short) FORMAT.getSampleSizeInBits());
        
        // data chunk
        out.write("data".getBytes());
        writeInt(out, audioDataLength);
    }

    private void writeInt(OutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 24) & 0xff);
    }

    private void writeShort(OutputStream out, short value) throws IOException {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }

    public boolean isRecording() {
        return recording;
    }
}
