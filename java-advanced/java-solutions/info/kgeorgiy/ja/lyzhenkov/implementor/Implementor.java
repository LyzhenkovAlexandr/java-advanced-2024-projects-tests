package info.kgeorgiy.ja.lyzhenkov.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.Tool;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * This class allows you to implement/extend the given interface/class.
 * To create a {@code .java} class, the {@link ClassSupplier} class is used.
 * The resulting classes can be packed into a {@code .jar} file.
 *
 * @author Lyzhenkov Alexander (alexanderlyzhenkov@gmail.com)
 */
public class Implementor implements JarImpler {
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String DOT = ".";
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String JAVA_SUFFIX = DOT + "java";
    /**
     * The constant is used in writing a class. The file path separator is taken depending on the system.
     */
    private static final String FILE_SEPARATOR = File.separator;
    /**
     * The constant is used to indicate the manifest version for the {@code .jar} file.
     */
    private static final String VERSION_MANIFEST = "1.0";
    /**
     * {@link FileVisitor} implementation for cleaning directories.
     *
     * @see SimpleFileVisitor
     */
    private static final FileVisitor<Path> visitorCleaning = new SimpleFileVisitor<>() {
        /**
         * Deletes visited files.
         *
         * @return always returns CONTINUE to continue visiting.
         */
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        /**
         * Deletes visited directories after all of their entries have been visited.
         *
         * @return always returns CONTINUE to continue visiting.
         */
        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    };

    /**
     * Default constructor
     */
    public Implementor() {
    }

    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        checkForImplementing(token);
        var classPath = createPathToFile(token, root);
        try (var writer = Files.newBufferedWriter(classPath, StandardCharsets.UTF_8)) {
            var characters = ClassSupplier.writeClass(token).toCharArray();
            for (var c : characters) {
                writer.write(String.format("\\u%04x", (int) c));
            }
        } catch (final IOException e) {
            throw new ImplerException("Failed to implement", e);
        }
    }

    @Override
    public void implementJar(Class<?> token, Path jarFile) throws ImplerException {
        var packagePath = token.getPackageName().replace(DOT, FILE_SEPARATOR);
        var packageAndSuffix = createPath(packagePath, ClassSupplier.getNameClass(token) + JAVA_SUFFIX);
        var absolutePathToToken = createAbsolutePath(token).toString();

        var tempDir = createTempDir(".", "temp");
        final Path tempBinDir;
        try {
            tempBinDir = createTempDir(".", "tempBin");
        } catch (final ImplerException e) {
            cleaningTempDirs(tempDir, null);
            throw e;
        }
        try {
            var fileForCompiler = tempDir.resolve(packageAndSuffix).toString();
            implement(token, tempDir);
            compilerFiles(tempBinDir.toString(), absolutePathToToken, fileForCompiler);
            var pathToClass = tempBinDir.resolve(createPath(
                    packagePath, ClassSupplier.getNameClass(token) + ".class"));
            var manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, VERSION_MANIFEST);
            try (final JarOutputStream jarWriter = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
                var zipEntry = new ZipEntry(createPath(
                        packagePath, ClassSupplier.getNameClass(token) + ".class").toString());
                jarWriter.putNextEntry(zipEntry);
                Files.copy(pathToClass, jarWriter);
                jarWriter.closeEntry();
            } catch (final SecurityException | IOException e) {
                throw new ImplerException("Failed to write to jar file: " + jarFile, e);
            }
        } finally {
            cleaningTempDirs(tempDir, tempBinDir);
        }
    }

    /**
     * Recursively cleans temporary directories by deleting all files and subdirectories within them.
     *
     * @param tempDir    the root directory of temporary files to be deleted.
     * @param tempBinDir the root directory of temporary binary files to be deleted.
     * @throws ImplerException if there are issues accessing files or deleting temporary directories,
     *                         an ImplerException is thrown with the appropriate message.
     * @see Files#walkFileTree(Path, FileVisitor)
     */
    private static void cleaningTempDirs(final Path tempDir, final Path tempBinDir) throws ImplerException {
        try {
            if (tempDir != null) {
                Files.walkFileTree(tempDir, visitorCleaning);
            }
            if (tempBinDir != null) {
                Files.walkFileTree(tempBinDir, visitorCleaning);
            }
        } catch (final SecurityException e) {
            throw new ImplerException("Could not access the folder or its subfolders", e);
        } catch (final IOException e) {
            throw new ImplerException("Failed to delete temporary folders", e);
        }
    }

    /**
     * Creates a {@link Path} by merging the specified strings into a single path.
     *
     * @param first  the first part of the path.
     * @param others additional strings representing subsequent parts of the path.
     * @return a Path representing the merged path.
     * @throws ImplerException if there is an error creating the path due to invalid path components,
     *                         an ImplerException is thrown with the appropriate message.
     */
    private static Path createPath(final String first, final String... others) throws ImplerException {
        try {
            return Path.of(first, others);
        } catch (final InvalidPathException e) {
            throw new ImplerException("Failed to merge paths: '" + first + "' with others" + Arrays.toString(others), e);
        }
    }

    /**
     * Compiles Java files using the system {@linkplain javax.tools.JavaCompiler Java compiler}.
     * If the {@code pathToBin} is not {@code null}, then the compiled files will be located along this path,
     * otherwise next to the Java source files.
     * Any generated compilation diagnostics will be written to {@code System.in}, {@code System.err}
     * and {@code System.out}.
     *
     * @param pathToBin the path to the directory where compiled files will be stored.
     * @param classpath the classpath to be used during compilation.
     * @param args      compilation files passed to the compiler.
     * @throws ImplerException - thrown with the corresponding error message
     *                         <ul>
     *                             <li>if the {@code args} or {@code classpath} are {@code null}</li>
     *                             <li>if the system Java compiler is {@code null}</li>
     *                             <li>if compilation fails with a non-zero exit code</li>
     *                         </ul>
     * @see Tool#run(InputStream, OutputStream, OutputStream, String...)
     */
    private static void compilerFiles(
            final String pathToBin,
            final String classpath,
            final String... args
    ) throws ImplerException {
        if (args == null || classpath == null) {
            throw new ImplerException("Files for compiler or classpath is null");
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new ImplerException("Compiler is null");
        }
        final int shift = pathToBin == null ? 0 : 2;
        var array = new String[2 + shift + args.length];
        array[shift] = "-cp";
        array[shift + 1] = classpath;
        if (shift == 2) {
            array[0] = "-d";
            array[1] = pathToBin;
        }
        System.arraycopy(args, 0, array, 4, args.length);
        final int result = compiler.run(null, null, null, array);
        if (result != 0) {
            throw new ImplerException("Failed compile java files, code error: " + result);
        }
    }

    /**
     * Creates an absolute path to the location of the class represented by the {@code Class<?>} object.
     *
     * @param token the Class object representing the class for which to create an absolute path.
     * @return the absolute path to the location of the class.
     * @throws ImplerException if failed to obtain the absolute path or convert URL to URI,
     *                         an ImplerException is thrown with the appropriate message.
     * @see Class#getProtectionDomain()
     * @see URL#toURI()
     */
    private static Path createAbsolutePath(final Class<?> token) throws ImplerException {
        try {
            return createPath(token.getProtectionDomain().getCodeSource().getLocation().toURI().toString());
        } catch (final SecurityException e) {
            throw new ImplerException("Failed to get absolute path", e);
        } catch (final URISyntaxException e) {
            throw new ImplerException("Failed to convert URL -> URI", e);
        }
    }

    /**
     * Creates a temporary directory with the specified {@code root} directory and {@code prefix}.
     *
     * @param root   the root directory where the temporary directory will be created.
     * @param prefix the prefix to be used for the name of the temporary directory.
     * @return a Path object representing the newly created temporary directory.
     * @throws ImplerException - is thrown with the relevant error details
     *                         <ul>
     *                             <li>if the {@code prefix} is invalid for creating a temporary folder</li>
     *                             <li>if there are {@linkplain SecurityException security} restrictions,
     *                             unsupported operations</li>
     *                         </ul>
     */
    private static Path createTempDir(final String root, final String prefix) throws ImplerException {
        try {
            var pathToTempDirs = createPath(root);
            return Files.createTempDirectory(pathToTempDirs, prefix);
        } catch (final IllegalArgumentException e) {
            throw new ImplerException("The prefix '" + prefix + "' cannot be used to create a temporary folder", e);
        } catch (final SecurityException | UnsupportedOperationException | IOException e) {
            throw new ImplerException("Failed to create temporary folder", e);
        }
    }

    /**
     * Checks whether a given class can be implemented or extended.
     *
     * @param token the Class object representing the class to be checked.
     * @throws ImplerException If the class represented by the {@code token} is an {@code array}, {@code primitive},
     *                         {@code enum}, {@code final}, {@code private}, or has only {@code private constructors},
     *                         it is impossible to implement or extend it.
     * @see Modifier
     */
    private static void checkForImplementing(final Class<?> token) throws ImplerException {
        var patternMessage = "Impossible to implements/extends %s class: " + token.getCanonicalName();
        if (token.isArray()) {
            throw new ImplerException(String.format(patternMessage, "array"));
        }
        if (token.isPrimitive()) {
            throw new ImplerException(String.format(patternMessage, "primitive"));
        }
        if (token == Enum.class) {
            throw new ImplerException(String.format(patternMessage, "enum"));
        }
        if (token == Record.class) {
            throw new ImplerException(String.format(patternMessage, "record"));
        }
        final int mod = token.getModifiers();
        if (Modifier.isFinal(mod)) {
            throw new ImplerException(String.format(patternMessage, "final"));
        }
        if (Modifier.isPrivate(mod)) {
            throw new ImplerException(String.format(patternMessage, "private"));
        }
        if (!Modifier.isInterface(mod)) {
            Arrays.stream(token.getDeclaredConstructors())
                    .filter(constructor -> !Modifier.isPrivate(constructor.getModifiers()))
                    .findFirst()
                    .orElseThrow(() -> new ImplerException("Impossible to extends class who have " +
                            "only private constructors: " + token.getCanonicalName()));
        }
    }

    /**
     * Creates a {@link Path} object representing the file path for the generated implementation file.
     * In this case, the {@link #JAVA_SUFFIX} is added to the simple class name
     * and creates parent directories for the resulting path.
     *
     * @param token the Class object representing the class for which the implementation file will be created.
     * @param root  the root directory where the implementation file will be located.
     * @return a Path object representing the file path for the generated implementation file.
     * @throws ImplerException if the input path is invalid, or if there are issues creating the parent directories.
     */
    private static Path createPathToFile(final Class<?> token, final Path root) throws ImplerException {
        var packageName = token.getPackageName().replace(DOT, FILE_SEPARATOR);
        var simpleClassName = ClassSupplier.getNameClass(token) + JAVA_SUFFIX;
        final Path classPath;
        try {
            classPath = root.resolve(Path.of(packageName, simpleClassName));
            if (classPath.getParent() != null) {
                Files.createDirectories(classPath.getParent());
            }
        } catch (final InvalidPathException e) {
            throw new ImplerException("Invalid input path '" + packageName + simpleClassName + "'", e);
        } catch (final IOException e) {
            throw new ImplerException("Failed to create parent directories", e);
        }
        return classPath;
    }

    /**
     * Entry point for the program. Parses input arguments and delegates to Implementor
     * for implementing classes or creating JAR files based on the provided arguments.
     * Prints error messages for invalid arguments or implementation failures in {@code System.err}.
     *
     * @param args expects either 2 arguments to implement the interface/class,
     *             where the first argument is the name of the binary interface/class that needs to be implemented,
     *             and the second is the root to save the resulting file.
     *             <p>
     *             Or 3 arguments, if you need to create a JAR file for the implemented interfaces/classes,
     *             with the first argument being {@code -jar} to create the JAR file, the second argument is similar
     *             to the previous situation,
     *             the third is the path to the expected jar file.
     */
    public static void main(String[] args) {
        if (args == null || (args.length != 2 && args.length != 3) || Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.err.println("Invalid input arguments");
            return;
        }
        try {
            if (args.length == 2) {
                new Implementor().implement(Class.forName(args[0]), createPath(args[1]));
            } else {
                if (!args[0].equals("-jar")) {
                    System.err.println("To create a jar-file, the first argument must be '-jar', " +
                            "but found: '" + args[0] + "'");
                    return;
                }
                if (!args[2].endsWith(".jar")) {
                    System.err.println("The file resolution was expected to be '.jar', but found: '" + args[2] + "'");
                    return;
                }
                new Implementor().implementJar(Class.forName(args[1]), createPath(args[2]));
            }
        } catch (final ClassNotFoundException e) {
            System.err.println("Class not found: " + e.getMessage());
        } catch (final ImplerException e) {
            System.err.println("Failed to implement: " + e.getMessage());
        }
    }
}
