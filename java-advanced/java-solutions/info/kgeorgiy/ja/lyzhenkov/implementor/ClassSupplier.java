package info.kgeorgiy.ja.lyzhenkov.implementor;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * This class creates content for the given interface/class.
 *
 * @author Lyzhenkov Alexander (alexanderlyzhenkov@gmail.com)
 * @see Implementor
 */
public class ClassSupplier {
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String EMPTY_STRING = "";
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String SPACE = " ";
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String SEMICOLON = ";";
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String DELIMITER_COMMA = ", ";
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String OPEN_CURLY_BRACKET = "{";
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String CLOSE_CURLY_BRACKET = "}";
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String OPEN_ROUND_BRACKET = "(";
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String CLOSE_ROUND_BRACKET = ")";
    /**
     * The constant is used to indicate tabs when writing a class.
     */
    private static final String TAB = "\t";
    /**
     * The constant is used to indicate double tab when writing a class.
     */
    private static final String DOUBLE_TAB = TAB + TAB;
    /**
     * The constant {@value} is used as an additional suffix to the name of the created class
     */
    private static final String CLASS_SUFFIX = "Impl";
    /**
     * The constant is used as a line feed, depending on the system
     *
     * @see System#lineSeparator()
     */
    private static final String LINE_SEPARATOR = System.lineSeparator();
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String STATIC = "static";
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String PUBLIC = "public";
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String PROTECTED = "protected";
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String PACKAGE_PRIVATE = EMPTY_STRING;
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String PRIVATE = "private";
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String PACKAGE = "package";
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String CLASS = "class";
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String IMPLEMENTS = "implements";
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String EXTENDS = "extends";
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String THROWS = "throws";
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String RETURN = "return";
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String SUPER = "super";
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String ZERO = "0";
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String NULL = "null";
    /**
     * The constant {@value} is used in writing a class.
     */
    private static final String FALSE = "false";

    /**
     * A record representing a wrapper for a {@link Method} object.
     * <p>
     * It provides customized implementations for {@link Object#equals(Object)} and {@link Object#hashCode()} methods
     * to ensure proper comparison and hashing based on the method name and parameter types.
     *
     * @param method method to wrap
     */
    private record Wrapper(Method method) {

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            var wrapper = (Wrapper) o;
            return Objects.equals(method.getName(), wrapper.method.getName())
                    && Arrays.equals(method.getParameterTypes(), wrapper.method.getParameterTypes());
        }

        @Override
        public int hashCode() {
            return Objects.hash(method.getName(), Arrays.hashCode(method.getParameterTypes()));
        }
    }

    /**
     * Default constructor
     */
    public ClassSupplier() {
    }

    /**
     * Generates a package declaration string based on the package of the provided Class object.
     *
     * @param token the Class object for which the package declaration string is generated.
     * @return a string representing the package declaration, or an empty string if there is no package.
     */
    private static String writePackage(final Class<?> token) {
        if (!token.getPackageName().isEmpty()) {
            return PACKAGE + SPACE + token.getPackageName() + SEMICOLON + LINE_SEPARATOR + LINE_SEPARATOR;
        }
        return EMPTY_STRING;
    }

    /**
     * Generates the opening signature for a Java class declaration based on the provided Class object.
     *
     * @param token the Class object for which the class signature is generated.
     * @return a string representing the opening signature of the Java class declaration.
     */
    private static String writeOpenClassSignature(final Class<?> token) {
        return String.join(
                SPACE,
                PUBLIC,
                CLASS,
                getNameClass(token),
                token.isInterface() ? IMPLEMENTS : EXTENDS,
                token.getCanonicalName(),
                OPEN_CURLY_BRACKET) + LINE_SEPARATOR;
    }

    /**
     * Generates source code for all non-private constructors declared in the class.
     *
     * @param token a Class object representing the class for which constructors are generated.
     * @return a string representing the source code of all non-private constructors.
     * @see #writeMethodOrConstructor(Executable, String, String, String, boolean)
     */
    private static String writeConstructors(final Class<?> token) {
        Predicate<Integer> condition = Modifier::isPrivate;
        var constructors = handleData(token.getDeclaredConstructors(),
                condition.negate(), Function.identity());
        return constructors.stream()
                .map(constructor -> writeMethodOrConstructor(
                        constructor,
                        token.getSimpleName() + CLASS_SUFFIX,
                        writeArgs(constructor.getParameters(), false),
                        EMPTY_STRING,
                        true))
                .collect(Collectors.joining());
    }

    /**
     * Generates source code for all abstract methods declared in a class and its superclasses.
     *
     * @param token a Class object representing the class for which the methods are generated.
     * @return a string representing the source code of all abstract methods.
     * @see #writeMethodOrConstructor(Executable, String, String, String, boolean)
     */
    private static String writeMethods(final Class<?> token) {
        var methods = handleData(token.getMethods(), Modifier::isAbstract, Wrapper::new);
        var tempToken = token;
        while (tempToken != null) {
            methods.addAll(handleData(token.getDeclaredMethods(), Modifier::isAbstract, Wrapper::new));
            tempToken = tempToken.getSuperclass();
        }
        return methods.stream()
                .map(Wrapper::method)
                .map(method -> writeMethodOrConstructor(
                        method,
                        method.getName(),
                        getDefaultValue(method),
                        method.getReturnType().getCanonicalName(),
                        false))
                .collect(Collectors.joining());
    }

    /**
     * Generates the closing signature for a Java class or method declaration.
     *
     * @return a string representing the closing signature of the Java class or method declaration.
     */
    private static String writeCloseSignature() {
        return CLOSE_CURLY_BRACKET + LINE_SEPARATOR;
    }

    /**
     * Generates source code for a method or constructor based on the provided executable,
     * the name, body of the method or constructor, return type, and a flag whether it is a constructor or not.
     *
     * @param executable    an executable object representing a method or constructor.
     * @param name          the name of the method or constructor.
     * @param body          the body of the method or constructor.
     * @param returnType    the type of the method's return value.
     * @param isConstructor a boolean value indicating whether this is a constructor or not.
     * @return a string representing the source code of the method or constructor.
     */
    private static String writeMethodOrConstructor(
            final Executable executable,
            final String name,
            final String body,
            final String returnType,
            final boolean isConstructor
    ) {
        return String.join(LINE_SEPARATOR,
                TAB + String.join(
                        SPACE,
                        getModifierMethods(executable.getModifiers()),
                        writeStaticModifier(executable.getModifiers()),
                        returnType,
                        name + writeArgs(executable.getParameters(), true),
                        writeThrowsException(executable),
                        OPEN_CURLY_BRACKET),
                DOUBLE_TAB + String.join(
                        SPACE,
                        isConstructor ? SUPER : RETURN,
                        body + SEMICOLON + LINE_SEPARATOR
                )) + TAB + CLOSE_CURLY_BRACKET + LINE_SEPARATOR + LINE_SEPARATOR;
    }

    /**
     * Generates the throws clause for an executable (method or constructor) declaration.
     *
     * @param executable the Executable object for which the throws clause is generated.
     * @return a string representing the throws clause of the executable, or an empty string
     * if no exceptions are thrown.
     */
    private static String writeThrowsException(final Executable executable) {
        var exceptions = Arrays.stream(executable.getExceptionTypes())
                .map(Class::getCanonicalName)
                .toList();
        return !exceptions.isEmpty() ? THROWS + SPACE + String.join(DELIMITER_COMMA, exceptions) : EMPTY_STRING;
    }

    /**
     * Processes an array of {@link Executable} elements based on the provided predicate and function.
     *
     * @param <T>       the type of Executable elements in the array.
     * @param <R>       the type of elements to be returned in the set.
     * @param data      an array of Executable elements to be processed.
     * @param predicate a predicate to filter the Executable elements based on their modifiers.
     * @param function  a function to apply to the filtered Executable elements to produce the resulting elements.
     * @return a mutable set of elements of type R, resulting from applying the function to the
     * filtered Executable elements.
     */
    private static <T extends Executable, R> Set<R> handleData(
            final T[] data,
            final Predicate<Integer> predicate,
            final Function<T, R> function
    ) {
        return Arrays.stream(data)
                .filter(method -> predicate.test(method.getModifiers()))
                .map(function)
                .collect(Collectors.toSet());
    }

    /**
     * Determines the access modifier of a method based on the provided modifier value.
     *
     * @param modifier the modifier value representing the access level of the method.
     * @return a string representing the access modifier of the method
     * ({@code public}, {@code protected}, {@code private}, or {@code package-private}).
     * @see Modifier
     */
    private static String getModifierMethods(final int modifier) {
        if (Modifier.isPublic(modifier)) {
            return PUBLIC;
        } else if (Modifier.isProtected(modifier)) {
            return PROTECTED;
        } else if (Modifier.isPrivate(modifier)) {
            return PRIVATE;
        }
        return PACKAGE_PRIVATE;
    }

    /**
     * Determines if the provided modifier represents a static method and returns {@link #STATIC} if true,
     * otherwise an {@link #EMPTY_STRING}.
     *
     * @param modifier the modifier value representing the attributes of the method.
     * @return {@link #STATIC} if the method is static, otherwise an {@link #EMPTY_STRING}.
     */
    private static String writeStaticModifier(final int modifier) {
        return Modifier.isStatic(modifier) ? STATIC : EMPTY_STRING;
    }

    /**
     * Generates a string of method arguments based on the supplied parameters.
     *
     * @param parameters an array of parameter objects representing the method parameters.
     * @param isImport   a boolean value indicating whether to include parameter types or not.
     *                   For example, if these are the arguments accepted by the method, then it is {@code true},
     *                   otherwise if we call the method and pass parameters to it, then {@code false}.
     * @return a string representing the method arguments
     * in the format "(arg1, arg2, ...)" or "(type1 arg1, type2 arg2, ...)".
     */
    private static String writeArgs(final Parameter[] parameters, final boolean isImport) {
        return OPEN_ROUND_BRACKET +
                Arrays.stream(parameters)
                        .map(argToken -> isImport ?
                                argToken.getType().getCanonicalName() + SPACE + argToken.getName() :
                                argToken.getName())
                        .collect(Collectors.joining(DELIMITER_COMMA)) +
                CLOSE_ROUND_BRACKET;
    }

    /**
     * Retrieves the default value for the return type of the provided {@link Method} object.
     *
     * @param method the Method object for which the default value is retrieved.
     * @return a string representing the default value for the return type
     * <ul>
     *      <li>if the return type is {@code void}, an {@link #EMPTY_STRING} is returned.</li>
     *      <li>if the return type is a primitive type, the appropriate default value ({@link #ZERO}
     *      or {@link #FALSE}) is returned.</li>
     *      <li>if the return type is a reference type, {@link #NULL} is returned.</li>
     * </ul>
     */
    private static String getDefaultValue(final Method method) {
        var returnType = method.getReturnType();
        if (returnType.equals(Void.TYPE)) {
            return EMPTY_STRING;
        }
        if (returnType.isPrimitive()) {
            if (returnType.equals(Boolean.TYPE)) {
                return FALSE;
            }
            return ZERO;
        }
        return NULL;
    }

    /**
     * Generates a class name with the {@linkplain #CLASS_SUFFIX specified suffix} based on the provided Class object.
     *
     * @param token the Class object for which the class name is generated.
     * @return a string representing the class name with the specified suffix appended.
     */
    static String getNameClass(final Class<?> token) {
        return token.getSimpleName() + CLASS_SUFFIX;
    }

    /**
     * Generates a complete Java class source code based on the provided Class object.
     *
     * @param token the Class object for which the class source code is generated.
     * @return a string representing the complete Java class source code.
     */
    public static String writeClass(final Class<?> token) {
        return writePackage(token) +
                writeOpenClassSignature(token) +
                writeConstructors(token) +
                writeMethods(token) +
                writeCloseSignature();
    }
}
