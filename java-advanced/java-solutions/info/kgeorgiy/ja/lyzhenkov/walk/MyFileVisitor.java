package info.kgeorgiy.ja.lyzhenkov.walk;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

public class MyFileVisitor extends SimpleFileVisitor<Path> {
    private final BufferedWriter writer;

    public MyFileVisitor(BufferedWriter writer) {
        this.writer = Objects.requireNonNull(writer, "Output stream not specified");
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        return super.preVisitDirectory(dir, attrs);
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        var fileName = file.toString();
        var hash = HashFile.calcHashJenkins(fileName);
        writer.write(hash + " " + fileName);
        writer.newLine();
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
        return visitFile(file, null);
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
        return FileVisitResult.CONTINUE;
    }
}
