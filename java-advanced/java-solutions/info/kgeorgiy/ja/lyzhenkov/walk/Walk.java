package info.kgeorgiy.ja.lyzhenkov.walk;

import java.io.BufferedWriter;
import java.io.IOException;

public class Walk extends AbstractWalk {

    @Override
    protected void writeHashFile(String inputFileName, BufferedWriter writerHash) throws IOException {
        var hash = HashFile.calcHashJenkins(inputFileName);
        writerHash.write(hash + " " + inputFileName);
        writerHash.newLine();
    }

    public static void main(String[] args) {
        new Walk().startWalk(args);
    }
}
