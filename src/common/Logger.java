package common;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static synchronized void log(String filePath, String line) {
        try {
            Path p = Path.of(filePath);
            if (p.getParent() != null) Files.createDirectories(p.getParent());

            try (FileWriter fw = new FileWriter(filePath, true)) {

                fw.write(System.lineSeparator());
                fw.write("------------------------------------------------" + System.lineSeparator());
                fw.write("[" + LocalDateTime.now().format(F) + "] " + line + System.lineSeparator());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
