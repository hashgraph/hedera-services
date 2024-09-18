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

package com.hedera.services.bdd.junit.hedera.utils;

import static java.util.Objects.requireNonNull;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;

import com.hedera.services.bdd.spec.props.JutilPropertySource;
import com.swirlds.platform.config.legacy.LegacyConfigPropertiesLoader;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WorkingDirUtils {
    private static final Logger log = LogManager.getLogger(WorkingDirUtils.class);
    private static final Path BASE_WORKING_LOC = Path.of("./build");
    private static final String DEFAULT_SCOPE = "hapi";
    private static final String KEYS_FOLDER = "keys";
    private static final String CONFIG_FOLDER = "config";
    private static final String LOG4J2_XML = "log4j2.xml";
    private static final String PROJECT_BOOTSTRAP_ASSETS_LOC = "hedera-node/configuration/dev";
    private static final String TEST_CLIENTS_BOOTSTRAP_ASSETS_LOC = "../configuration/dev";

    public static final String DATA_DIR = "data";
    public static final String CONFIG_DIR = "config";
    public static final String OUTPUT_DIR = "output";
    public static final String UPGRADE_DIR = "upgrade";
    public static final String CURRENT_DIR = "current";
    public static final String CONFIG_TXT = "config.txt";
    public static final String GENESIS_PROPERTIES = "genesis.properties";
    public static final String ERROR_REDIRECT_FILE = "test-clients.log";
    public static final String STATE_METADATA_FILE = "stateMetadata.txt";
    public static final String APPLICATION_PROPERTIES = "application.properties";

    private static final List<String> WORKING_DIR_DATA_FOLDERS = List.of(KEYS_FOLDER, CONFIG_FOLDER, UPGRADE_DIR);

    private WorkingDirUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Returns the path to the working directory for the given node ID.
     *
     * @param nodeId the ID of the node
     * @param scope if non-null, an additional scope to use for the working directory
     * @return the path to the working directory
     */
    public static Path workingDirFor(final long nodeId, @Nullable String scope) {
        scope = scope == null ? DEFAULT_SCOPE : scope;
        return BASE_WORKING_LOC
                .resolve(scope + "-test")
                .resolve("node" + nodeId)
                .normalize();
    }

    /**
     * Initializes the working directory by deleting it and creating a new one
     * with the given <i>config.txt</i> file.
     *
     * @param workingDir the path to the working directory
     * @param configTxt the contents of the <i>config.txt</i> file
     */
    public static void recreateWorkingDir(@NonNull final Path workingDir, @NonNull final String configTxt) {
        // Clean up any existing directory structure
        rm(workingDir);
        // Initialize the data folders
        WORKING_DIR_DATA_FOLDERS.forEach(folder ->
                createDirectoriesUnchecked(workingDir.resolve(DATA_DIR).resolve(folder)));
        // Initialize the current upgrade folder
        createDirectoriesUnchecked(
                workingDir.resolve(DATA_DIR).resolve(UPGRADE_DIR).resolve(CURRENT_DIR));
        // Write the address book (config.txt)
        writeStringUnchecked(workingDir.resolve(CONFIG_TXT), configTxt);
        // Copy the bootstrap assets into the working directory
        copyBootstrapAssets(bootstrapAssetsLoc(), workingDir);
        // Update the log4j2.xml file with the correct output directory
        updateLog4j2XmlOutputDir(workingDir);
    }

    /**
     * Updates the <i>upgrade.artifacts.path</i> property in the <i>application.properties</i> file
     *
     * @param propertiesPath the path to the <i>application.properties</i> file
     * @param upgradeArtifactsPath the path to the upgrade artifacts directory
     */
    public static void updateUpgradeArtifactsProperty(
            @NonNull final Path propertiesPath, @NonNull final Path upgradeArtifactsPath) {
        final var properties = new Properties();
        try {
            try (final var in = Files.newInputStream(propertiesPath)) {
                properties.load(in);
            }
            properties.setProperty("networkAdmin.upgradeArtifactsPath", upgradeArtifactsPath.toString());
            try (final var out = Files.newOutputStream(propertiesPath)) {
                properties.store(out, null);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the path to the <i>application.properties</i> file used to bootstrap an embedded or subprocess network.
     *
     * @return the path to the <i>application.properties</i> file
     */
    public static JutilPropertySource hapiTestStartupProperties() {
        return new JutilPropertySource(bootstrapAssetsLoc().resolve(APPLICATION_PROPERTIES));
    }

    private static Path bootstrapAssetsLoc() {
        return Paths.get(System.getProperty("user.dir")).endsWith("hedera-services")
                ? Path.of(PROJECT_BOOTSTRAP_ASSETS_LOC)
                : Path.of(TEST_CLIENTS_BOOTSTRAP_ASSETS_LOC);
    }

    private static void updateLog4j2XmlOutputDir(@NonNull final Path workingDir) {
        final var path = workingDir.resolve(LOG4J2_XML);
        final var log4j2Xml = readStringUnchecked(path);
        final var updatedLog4j2Xml = log4j2Xml
                .replace(
                        "</Appenders>\n" + "  <Loggers>",
                        """
                                  <RollingFile name="TestClientRollingFile" fileName="output/test-clients.log"
                                    filePattern="output/test-clients-%d{yyyy-MM-dd}-%i.log.gz">
                                    <PatternLayout>
                                      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5p %-4L %c{1} - %m{nolookups}%n</pattern>
                                    </PatternLayout>
                                    <Policies>
                                      <TimeBasedTriggeringPolicy/>
                                      <SizeBasedTriggeringPolicy size="100 MB"/>
                                    </Policies>
                                    <DefaultRolloverStrategy max="10">
                                      <Delete basePath="output" maxDepth="3">
                                        <IfFileName glob="test-clients-*.log.gz">
                                          <IfLastModified age="P3D"/>
                                        </IfFileName>
                                      </Delete>
                                    </DefaultRolloverStrategy>
                                  </RollingFile>
                                </Appenders>
                                <Loggers>

                                  <Logger name="com.hedera.services.bdd" level="info" additivity="false">
                                    <AppenderRef ref="Console"/>
                                    <AppenderRef ref="TestClientRollingFile"/>
                                  </Logger>
                                 \s""")
                .replace(
                        "output/",
                        workingDir.resolve(OUTPUT_DIR).toAbsolutePath().normalize() + "/");
        writeStringUnchecked(path, updatedLog4j2Xml, StandardOpenOption.WRITE);
    }

    /**
     * Recursively deletes the given path.
     *
     * @param path the path to delete
     */
    public static void rm(@NonNull final Path path) {
        if (Files.exists(path)) {
            try (Stream<Path> paths = Files.walk(path)) {
                paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * Returns the given path as a file after a best-effort attempt to ensure it exists.
     *
     * @param path the path to ensure exists
     * @return the path as a file
     */
    public static File guaranteedExtantFile(@NonNull final Path path) {
        if (!Files.exists(path)) {
            try {
                Files.createFile(guaranteedExtantDir(path.getParent()).resolve(path.getName(path.getNameCount() - 1)));
            } catch (IOException ignore) {
                // We don't care if the file already exists
            }
        }
        return path.toFile();
    }

    /**
     * Returns the given path after a best-effort attempt to ensure it exists.
     *
     * @param path the path to ensure exists
     * @return the path
     */
    public static Path guaranteedExtantDir(@NonNull final Path path) {
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                // We don't care if the directory already exists
            }
        }
        return path;
    }

    private static String readStringUnchecked(@NonNull final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeStringUnchecked(
            @NonNull final Path path, @NonNull final String content, @NonNull final OpenOption... options) {
        try {
            Files.writeString(path, content, options);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void createDirectoriesUnchecked(@NonNull final Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copyBootstrapAssets(@NonNull final Path assetDir, @NonNull final Path workingDir) {
        try (final var files = Files.walk(assetDir)) {
            files.filter(file -> !file.equals(assetDir)).forEach(file -> {
                if (file.getFileName().toString().endsWith(".properties")) {
                    copyUnchecked(
                            file,
                            workingDir
                                    .resolve(DATA_DIR)
                                    .resolve(CONFIG_FOLDER)
                                    .resolve(file.getFileName().toString()));
                } else {
                    copyUnchecked(file, workingDir.resolve(file.getFileName().toString()));
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Copy a file from the source path to the target path, throwing an unchecked exception if an
     * {@link IOException} occurs.
     *
     * @param source the source path
     * @param target the target path
     */
    public static void copyUnchecked(@NonNull final Path source, @NonNull final Path target) {
        try {
            Files.copy(source, target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Ensure a directory exists at the given path, creating it if necessary.
     *
     * @param path The path to ensure exists as a directory.
     */
    public static void ensureDir(@NonNull final String path) {
        requireNonNull(path);
        final var f = new File(path);
        if (!f.exists() && !f.mkdirs()) {
            throw new IllegalStateException("Failed to create directory: " + f.getAbsolutePath());
        }
    }

    /**
     * Load the address book from the given path, using {@link RandomAddressBookBuilder} to
     * set a {@code sigCert} for each address.
     *
     * @param path the path to the address book file
     * @return the loaded address book
     */
    public static AddressBook loadAddressBook(@NonNull final Path path) {
        requireNonNull(path);
        final var configFile = LegacyConfigPropertiesLoader.loadConfigFile(path.toAbsolutePath());
        final var randomAddressBook = RandomAddressBookBuilder.create(new Random())
                .withSize(1)
                .withRealKeysEnabled(true)
                .build();
        final var sigCert = requireNonNull(randomAddressBook.iterator().next().getSigCert());
        final var addressBook = configFile.getAddressBook();
        return new AddressBook(stream(spliteratorUnknownSize(addressBook.iterator(), 0), false)
                .map(address -> address.copySetSigCert(sigCert))
                .toList());
    }

    public static AddressBook loadAddressBookWithDeterministicCerts(@NonNull final Path path) {
        requireNonNull(path);
        final var configFile = LegacyConfigPropertiesLoader.loadConfigFile(path.toAbsolutePath());
        try {
            final var addressBook = configFile.getAddressBook();
            CryptoStatic.generateKeysAndCerts(addressBook);
            return addressBook;
        } catch (Exception e) {
            throw new RuntimeException("Error generating keys and certs", e);
        }
    }
}
