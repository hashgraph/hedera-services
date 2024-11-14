/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.tss.cryptography.nativesupport;

import com.hedera.common.nativesupport.internal.RunOnlyOnce;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * <p>
 * Handles loading of native binary libraries from within a JAR file based on operating system and architecture.
 *  <p>
 *  Since it is not possible to directly {@link System#load(String)} a library from within a JAR, this class facilitates
 * the extraction and loading of these libraries when they are not pre-installed on the operating system.
 * <p>
 * The class provides mechanisms to extract the library from the JAR, store it in a temporary directory on the file system,
 * set appropriate file permissions, and finally load the library into the application.
 *
 * @implNote Libraries are expected to be organized within the JAR file at {@code /software/<os>/<arch>/name}.
 * This path structure is used to construct the location of the library based on the current operating
 * system and architecture, ensuring only the correct version of the library is loaded according to the executing environment.
 ** <p>
 * As JPMS does not allow for resources contained in a module to be loaded in a separated class it is NOT responsibility of this class
 * to retrieve the {@link InputStream} in the jar by invoking the classloader.
 * <p>
 *  Callers must use any of the suitable methods including:
 * <ul>
 * <li>{@link Class#getResourceAsStream(String)}
 * <li>{@link Module#getResourceAsStream(String)}
 * </ul>
 *
 * Example usage:
 * <pre>
 * {@code
 * NativeLibrary library = NativeLibrary.withName("example");
 * library.install(getClass().getResourceAsStream(library.locationInJar()));
 * }
 * </pre>
 *
 * @see Architecture Current system architecture (e.g., x86, x64).
 * @see OperatingSystem Current operating system (e.g., Windows, Linux, macOS).
 */
public class NativeLibrary {
    /**
     * The root resources folder where the software is located.
     */
    private static final String SOFTWARE_FOLDER_NAME = "software";

    /**
     * The path delimiter used in the JAR file.
     */
    private static final String RESOURCE_PATH_DELIMITER = File.separator;

    /**
     * Default extensions for binary libraries per OS
     */
    private static final Map<OperatingSystem, String> DEFAULT_LIB_EXTENSIONS =
            Map.of(OperatingSystem.WINDOWS, "dll", OperatingSystem.LINUX, "so", OperatingSystem.DARWIN, "dylib");

    /**
     * Default prefix for binary libraries per OS
     */
    private static final Map<OperatingSystem, String> DEFAULT_LIB_PREFIXES =
            Map.of(OperatingSystem.WINDOWS, "", OperatingSystem.LINUX, "lib", OperatingSystem.DARWIN, "lib");

    /** Ensures that a library with a given name is loaded only once */
    private static final RunOnlyOnce<String> runOnlyOnce = new RunOnlyOnce<>();

    private final String name;
    private final Map<OperatingSystem, String> libExtensions;
    private final Map<OperatingSystem, String> libPrefixes;

    /**
     *
     * @implNote This method expects the executable to be present at the following location in the JAR file:
     * {@code /software/<os>/<arch>/name}.
     *
     * @param name the library to load.
     * @param libExtensions defaults extensions for each os to use to load the library
     */
    private NativeLibrary(
            @NonNull final String name,
            @NonNull final Map<OperatingSystem, String> libPrefixes,
            @NonNull final Map<OperatingSystem, String> libExtensions) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.libExtensions = Map.copyOf(Objects.requireNonNull(libExtensions, "libExtensions must not be null"));
        this.libPrefixes = Map.copyOf(Objects.requireNonNull(libPrefixes, "libPrefixes must not be null"));
    }

    /**
     * Factory method to create a NativeLibrary instance with custom library extensions.
     *
     * @param name The name of the library.
     * @param libExtensions Custom library file extensions for each operating system.
     * @return An instance of NativeLibrary.
     */
    @NonNull
    public static NativeLibrary withName(
            @NonNull final String name,
            @NonNull final Map<OperatingSystem, String> libPrefixes,
            @NonNull final Map<OperatingSystem, String> libExtensions) {
        return new NativeLibrary(name, libPrefixes, libExtensions);
    }

    /**
     * Factory method to create a {@link NativeLibrary} instance using default library extensions.
     *
     * @param name The name of the library.
     * @return An instance of NativeLibrary.
     */
    @NonNull
    public static NativeLibrary withName(@NonNull final String name) {
        return withName(name, DEFAULT_LIB_PREFIXES, DEFAULT_LIB_EXTENSIONS);
    }

    /**
     * Returns the library name.
     *
     * @return the library name
     */
    @NonNull
    public String name() {
        return this.name;
    }

    /**
     * Constructs the relative path within the JAR file for the library based on the operating system and architecture.
     *
     * @return A string representing the relative path.
     */
    @NonNull
    public String locationInJar() {
        return locationInJar(this.name, this.libPrefixes, this.libExtensions);
    }

    /**
     * Static helper to construct the library path in the JAR file for a given library name and extensions map.
     *
     * @param libraryName The name of the library.
     * @param libExtensions Library file extensions for each operating system.
     * @return The path to the library in the JAR file.
     */
    @NonNull
    public static String locationInJar(
            @NonNull final String libraryName,
            @NonNull final Map<OperatingSystem, String> libPrefixes,
            @NonNull final Map<OperatingSystem, String> libExtensions) {
        Objects.requireNonNull(libraryName, "name must not be null");
        Objects.requireNonNull(libPrefixes, "libPrefixes must not be null");
        Objects.requireNonNull(libExtensions, "libExtensions must not be null");
        final OperatingSystem os = OperatingSystem.current();
        final Architecture arch = Architecture.current();

        final String libPrefix = libPrefixes.getOrDefault(os, "");

        String libExtension = libExtensions.get(os);
        if (libExtension != null && !libExtension.isEmpty()) {
            libExtension = "." + libExtension;
        } else {
            libExtension = "";
        }
        return SOFTWARE_FOLDER_NAME
                + RESOURCE_PATH_DELIMITER
                + os.name().toLowerCase(Locale.US)
                + RESOURCE_PATH_DELIMITER
                + arch.name().toLowerCase(Locale.US)
                + RESOURCE_PATH_DELIMITER
                + libPrefix
                + libraryName
                + libExtension;
    }

    /**
     * Copied from jdk.internal.module.Resources.toPackageName() since the method is not open to the public.
     *
     * @return the package name where the native library is located
     */
    public String packageNameOfResource() {
        final String name = locationInJar();
        int index = name.lastIndexOf('/');
        if (index == -1 || index == name.length() - 1) {
            return "";
        } else {
            return name.substring(0, index).replace('/', '.');
        }
    }

    /**
     * Unpackages the native library to a temporary dir, sets appropriate file permissions, and loads the library into
     * the JVM.
     *
     * @param c the class whose module contains the native library
     * @throws IllegalStateException if the module does not open the package where the resource is located
     */
    public void install(@NonNull final Class<?> c) {
        if (!c.getModule().isOpen(packageNameOfResource(), this.getClass().getModule())) {
            // getResourceAsStream() will not throw an exception if the package is not opened, it will just return null
            // so we manually check if the package is opened
            throw new IllegalStateException("The module '%s' must open the package '%s' to module '%s'"
                    .formatted(
                            c.getModule().getName(),
                            packageNameOfResource(),
                            this.getClass().getModule().getName()));
        }
        runOnlyOnce.runIfNeeded(name, () -> installUnchecked(c));
    }

    /**
     * Calls the {@link #install(InputStream)} method and catches any checked exceptions, rethrowing them as unchecked exceptions.
     */
    private void installUnchecked(@NonNull final Class<?> c) {
        try {
            install(c.getModule().getResourceAsStream(locationInJar()));
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load adapter " + name(), new IOException(e));
        }
    }

    /**
     * Unpackages the native library from a provided InputStream to a temporary dir, sets appropriate file permissions, and loads the library
     * into the JVM.
     * <p>Warning: It is responsibility of the caller to assure this method is only called once per desired library.
     * @param resourceStream An InputStream of the library file.
     * @throws IOException if there's an error reading the file or setting permissions.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void install(@NonNull final InputStream resourceStream) throws IOException {
        Objects.requireNonNull(resourceStream, "resourceStream must not be null");
        final OperatingSystem os = OperatingSystem.current();
        final String libName =
                os == OperatingSystem.WINDOWS ? name + "." + libExtensions.get(OperatingSystem.WINDOWS) : name;
        final String fileName = Path.of(libName).getFileName().toString();
        final Path tempDirectory = createTempDirectory();
        final Path tempFile = tempDirectory.resolve(fileName);

        Files.copy(resourceStream, tempFile);
        if (isPosixCompliant()) {
            Files.setPosixFilePermissions(tempFile, PosixFilePermissions.fromString("rwxrwxrwx"));
        } else {
            final File f = tempFile.toFile();
            f.setExecutable(true, false);
            f.setReadable(true, false);
            f.setWritable(true, false);
        }

        System.load(tempFile.toAbsolutePath().toString());
    }

    /**
     * Creates a temporary directory for the requester.
     *
     * @return the path to the temporary directory.
     * @throws IOException if the temporary directory cannot be created or an I/O error occurs.
     */
    @NonNull
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private Path createTempDirectory() throws IOException {
        final Path tempDirectory = Files.createTempDirectory(name);
        tempDirectory.toFile().mkdir();
        tempDirectory.toFile().deleteOnExit();
        return tempDirectory;
    }

    /**
     * Is the system we're running on Posix compliant?
     *
     * @return True if posix compliant.
     */
    static boolean isPosixCompliant() {
        try {
            return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        } catch (FileSystemNotFoundException | ProviderNotFoundException | SecurityException e) {
            return false;
        }
    }
}
