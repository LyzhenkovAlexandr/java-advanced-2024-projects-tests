package info.kgeorgiy.ja.lyzhenkov.walk;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public abstract class AbstractWalk {

    protected static final String HASH_ERROR = "0".repeat(8);
    protected FileVisitor<Path> visitor;
    private Path in;
    private Path out;

    public void startWalk(String[] args) {
        if (!checkArgs(args)) {
            return;
        }

        try (var readerPaths = Files.newBufferedReader(in, StandardCharsets.UTF_8)) {
            if (out.getParent() != null) {
                Files.createDirectories(out.getParent());
            }
            try (var writerHash = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
                visitor = new MyFileVisitor(writerHash);
                String inputFileName;
                while ((inputFileName = readerPaths.readLine()) != null) {
                    writeHashFile(inputFileName, writerHash);
                }
            } catch (IOException e) {
                System.err.println("Error to file " + e.getMessage());
            }
        } catch (FileAlreadyExistsException e) {
            System.err.println(e.getMessage());
        } catch (FileNotFoundException e) {
            System.err.println("File not found " + e.getMessage());
        } catch (SecurityException e) {
            System.err.println("Insufficient rights " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error input file " + e.getMessage());
        }
    }

    protected abstract void writeHashFile(String inputFileName, BufferedWriter writerHash) throws IOException;

    private boolean checkArgs(String[] args) {
        if (args == null || args.length != 2 || args[0] == null || args[1] == null) {
            System.err.println("The input format should be java Walk <input file> <output file>");
            return false;
        }
        try {
            in = Path.of(args[0]);
        } catch (InvalidPathException e) {
            System.err.println("Invalid input path '" + args[0] + "'");
            return false;
        }
        try {
            out = Path.of(args[1]);
        } catch (InvalidPathException e) {
            System.err.println("Invalid output path '" + args[1] + "'");
            return false;
        }
        return true;
    }
}
