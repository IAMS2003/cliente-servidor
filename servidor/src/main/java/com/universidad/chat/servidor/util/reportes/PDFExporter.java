package com.universidad.chat.servidor.util.reportes;

import com.universidad.chat.servidor.model.Canal;
import com.universidad.chat.servidor.model.MensajeLog;
import com.universidad.chat.servidor.model.Usuario;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PDFExporter {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static void ensureDir(Path path) throws IOException {
        Path dir = path.getParent();
        if (dir != null && !Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

    public static Path exportUsuarios(List<Usuario> usuarios, Path destino) throws IOException {
        ensureDir(destino);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            float margin = 50;
            float y = page.getMediaBox().getHeight() - margin;
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            try {
                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("Usuarios registrados");
                cs.endText();
                cs.setFont(PDType1Font.HELVETICA, 10);
                y -= 24;
                for (Usuario u : usuarios) {
                    if (y < margin + 40) {
                        cs.close();
                        page = new PDPage(PDRectangle.LETTER);
                        doc.addPage(page);
                        y = page.getMediaBox().getHeight() - margin;
                        cs = new PDPageContentStream(doc, page);
                        cs.setFont(PDType1Font.HELVETICA, 10);
                    }
                    String srv = (u.getServidorHost()!=null?u.getServidorHost():"-") + ":" + (u.getServidorPuerto()!=null?u.getServidorPuerto():0);
                    String line = String.format("ID:%d | %s | %s | conectado:%s | Servidor:%s", u.getId(), u.getNombreUsuario(), u.getEmail(), u.isConectado(), srv);
                    cs.beginText();
                    cs.newLineAtOffset(margin, y);
                    cs.showText(line);
                    cs.endText();
                    y -= 14;
                }
            } finally {
                cs.close();
            }
            doc.save(destino.toFile());
        }
        return destino;
    }

    public static Path exportConectados(List<Usuario> conectados, Path destino) throws IOException {
        ensureDir(destino);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            float margin = 50;
            float y = page.getMediaBox().getHeight() - margin;
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            try {
                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("Usuarios conectados");
                cs.endText();

                cs.setFont(PDType1Font.HELVETICA, 10);
                y -= 24;
                for (Usuario u : conectados) {
                    if (y < margin + 40) {
                        cs.close();
                        page = new PDPage(PDRectangle.LETTER);
                        doc.addPage(page);
                        y = page.getMediaBox().getHeight() - margin;
                        cs = new PDPageContentStream(doc, page);
                        cs.setFont(PDType1Font.HELVETICA, 10);
                    }
                    String srv = (u.getServidorHost()!=null?u.getServidorHost():"-") + ":" + (u.getServidorPuerto()!=null?u.getServidorPuerto():0);
                    String ip = (u.getDireccionIP()!=null?u.getDireccionIP():"-") + (u.getPuertoConexion()!=null?":"+u.getPuertoConexion():"");
                    String line = String.format("ID:%d | %s | IP:%s | Servidor:%s", u.getId(), u.getNombreUsuario(), ip, srv);
                    cs.beginText();
                    cs.newLineAtOffset(margin, y);
                    cs.showText(line);
                    cs.endText();
                    y -= 14;
                }
            } finally {
                cs.close();
            }
            doc.save(destino.toFile());
        }
        return destino;
    }

    public static Path exportCanalesYMiembros(List<Canal> canales, java.util.function.IntFunction<List<Usuario>> miembrosProvider, Path destino) throws IOException {
        ensureDir(destino);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            float margin = 50;
            float y = page.getMediaBox().getHeight() - margin;
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            try {
                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("Canales y miembros");
                cs.endText();
                cs.setFont(PDType1Font.HELVETICA, 10);
                y -= 24;
                for (Canal c : canales) {
                    if (y < margin + 54) { // header + at least two lines
                        cs.close();
                        page = new PDPage(PDRectangle.LETTER);
                        doc.addPage(page);
                        y = page.getMediaBox().getHeight() - margin;
                        cs = new PDPageContentStream(doc, page);
                        cs.setFont(PDType1Font.HELVETICA, 10);
                    }
                    String header = String.format("Canal #%d: %s%s", c.getId(), c.getNombre(), c.isEsPrivado()?" (privado)":"");
                    cs.beginText();
                    cs.newLineAtOffset(margin, y);
                    cs.showText(header);
                    cs.endText();
                    y -= 14;

                    List<Usuario> miembros = miembrosProvider.apply(c.getId());
                    for (Usuario u : miembros) {
                        if (y < margin + 30) {
                            cs.close();
                            page = new PDPage(PDRectangle.LETTER);
                            doc.addPage(page);
                            y = page.getMediaBox().getHeight() - margin;
                            cs = new PDPageContentStream(doc, page);
                            cs.setFont(PDType1Font.HELVETICA, 10);
                        }
                        String line = String.format("   - %s (ID:%d)", u.getNombreUsuario(), u.getId());
                        cs.beginText();
                        cs.newLineAtOffset(margin + 20, y);
                        cs.showText(line);
                        cs.endText();
                        y -= 14;
                    }
                    y -= 6;
                }
            } finally {
                cs.close();
            }
            doc.save(destino.toFile());
        }
        return destino;
    }

    public static Path exportAudioLogs(List<MensajeLog> audioLogs, Path destino) throws IOException {
        ensureDir(destino);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            float margin = 50;
            float y = page.getMediaBox().getHeight() - margin;
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            try {
                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("Mensajes de audio");
                cs.endText();
                cs.setFont(PDType1Font.HELVETICA, 10);
                y -= 24;
                for (MensajeLog m : audioLogs) {
                    String[] lines = new String[]{
                        String.format("ID:%d | Em:%d -> Re:%d | Canal:%s | %s", m.getId(), m.getIdEmisor(), m.getIdReceptor(), m.getIdCanal(), m.getFecha()!=null?FMT.format(m.getFecha()):""),
                        String.format("Archivo: %s", m.getArchivoAudio()),
                        String.format("Transcripción: %s", m.getTranscripcion()!=null?m.getTranscripcion():""),
                        ""
                    };
                    for (String ln : lines) {
                        if (y < margin + 30) {
                            cs.close();
                            page = new PDPage(PDRectangle.LETTER);
                            doc.addPage(page);
                            y = page.getMediaBox().getHeight() - margin;
                            cs = new PDPageContentStream(doc, page);
                            cs.setFont(PDType1Font.HELVETICA, 10);
                        }
                        cs.beginText();
                        cs.newLineAtOffset(margin, y);
                        cs.showText(trimToWidth(ln, 100));
                        cs.endText();
                        y -= 14;
                    }
                }
            } finally {
                cs.close();
            }
            doc.save(destino.toFile());
        }
        return destino;
    }

    public static Path exportLogs(List<MensajeLog> logs, Path destino) throws IOException {
        ensureDir(destino);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            float margin = 50;
            float y = page.getMediaBox().getHeight() - margin;
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            try {
                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("Logs de mensajes");
                cs.endText();
                cs.setFont(PDType1Font.HELVETICA, 10);
                y -= 24;
                for (MensajeLog m : logs) {
                    String servidor = "";
                    if (m.getServidorHost() != null) {
                        servidor = m.getServidorHost() + (m.getServidorPuerto() != null ? (":" + m.getServidorPuerto()) : "");
                    }
                    String[] lines = new String[]{
                        String.format("ID:%d | Tipo:%s | Em:%d -> Re:%d | Canal:%s | Servidor:%s | %s", m.getId(), m.getTipoMensaje(), m.getIdEmisor(), m.getIdReceptor(), m.getIdCanal(), servidor, m.getFecha()!=null?FMT.format(m.getFecha()):""),
                        String.format("Contenido: %s", m.getContenido()!=null?m.getContenido():""),
                        ""
                    };
                    for (String ln : lines) {
                        if (y < margin + 30) {
                            cs.close();
                            page = new PDPage(PDRectangle.LETTER);
                            doc.addPage(page);
                            y = page.getMediaBox().getHeight() - margin;
                            cs = new PDPageContentStream(doc, page);
                            cs.setFont(PDType1Font.HELVETICA, 10);
                        }
                        cs.beginText();
                        cs.newLineAtOffset(margin, y);
                        cs.showText(trimToWidth(ln, 100));
                        cs.endText();
                        y -= 14;
                    }
                }
            } finally {
                cs.close();
            }
            doc.save(destino.toFile());
        }
        return destino;
    }

    private static String trimToWidth(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars - 3) + "...";
    }
}
