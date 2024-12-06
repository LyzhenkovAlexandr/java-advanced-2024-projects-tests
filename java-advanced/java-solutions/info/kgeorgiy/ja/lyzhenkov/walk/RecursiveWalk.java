package info.kgeorgiy.ja.lyzhenkov.walk;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public class RecursiveWalk extends AbstractWalk {

    @Override
    protected void writeHashFile(String inputFileName, BufferedWriter writerHash) throws IOException {
        final Path start;
        try {
            start = Path.of(inputFileName);
        } catch (InvalidPathException e) {
            writerHash.write(HASH_ERROR + " " + inputFileName);
            writerHash.newLine();
            System.err.println("Invalid path '" + inputFileName + "'");
            return;
        }
        Files.walkFileTree(start, visitor);
    }

    public static void main(String[] args) {
        new RecursiveWalk().startWalk(args);
    }
}
