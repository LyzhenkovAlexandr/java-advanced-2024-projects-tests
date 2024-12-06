package info.kgeorgiy.ja.lyzhenkov.walk;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class HashFile {
    private static final int BUFFER_SIZE = 1 << 11;
    private static final String HASH_ERROR = "0".repeat(8);
    private static final byte[] BUFFER = new byte[BUFFER_SIZE];

    public static String calcHashJenkins(final String inputFileName) {
        final Path pathInput;
        try {
            pathInput = Path.of(inputFileName);
        } catch (InvalidPathException e) {
            System.err.println("Invalid path: " + e.getMessage());
            return HASH_ERROR;
        }
        int hash = 0;
        try (final InputStream reader = Files.newInputStream(pathInput, StandardOpenOption.READ)) {
            int c;
            while ((c = reader.read(BUFFER)) >= 0) {
                for (int i = 0; i < c; i++) {
                    hash += (BUFFER[i] & 0xff);
                    hash += (hash << 10);
                    hash ^= (hash >>> 6);
                }
            }
            hash += (hash << 3);
            hash ^= (hash >>> 11);
            hash += (hash << 15);
        } catch (IOException e) {
            System.err.println("Error reading file named '" + inputFileName + "'");
            return HASH_ERROR;
        }
        if (hash == 0) {
            return HASH_ERROR;
        }
        return String.format("%08x", hash);
    }
}
