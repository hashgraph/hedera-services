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

package com.hedera.services.bdd.spec.utilops.upgrade;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.services.bdd.junit.hedera.subprocess.SubProcessNetwork;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A utility operation that builds a upgrade zip file based on the last JAR assembled
 * with this commit of the services repo
 *
 * <p>It builds the upgrade file from maybe three ingredients,
 * <ol>
 *     <li>A {@code HederaNode.jar} obtained by unzipping the JAR at the given path
 *     and replacing the Services version key in <i>semantic-version.properties</i>
 *     with a given new version.</li>
 *     <li><b>(Optional)</b> A <i>settings.txt</i> at the given path.</li>
 *     <li><b>(Optional)</b> A <i>config.txt</i> obtained by removing a requested
 *     node from the target network's current <i>config.txt</i>. If this is specified,
 *     the target network must be a {@link SubProcessNetwork}</li>, since this is
 *     the only case in which the test can meaningfully inspect and change the
 *     address book.
 * </ol>
 */
public class BuildUpgradeZipOp extends UtilOp {
    private static final String JAR_FILE = "HederaNode.jar";
    private static final String SEM_VER_FILE = "semantic-version.properties";
    private static final String SERVICES_VERSION_PROP = "hedera.services.version";
    private static final Path WORKING_PATH = Path.of("build");
    private static final Path UPGRADE_ZIP_PATH = WORKING_PATH.resolve("upgrade");
    private static final Path EXPLODED_ZIP_PATH = WORKING_PATH.resolve("exploded");
    private static final Path NEW_JAR_PATH = UPGRADE_ZIP_PATH.resolve(JAR_FILE);

    public static final Path CURRENT_JAR_PATH =
            Path.of("../").resolve("data").resolve("apps").resolve(JAR_FILE);
    public static final Path DEFAULT_UPGRADE_ZIP_LOC = WORKING_PATH.resolve("upgrade.zip");

    /**
     * The path of the {@code HederaNode.jar} currently running in the network.
     */
    private final Path runningJarLoc;
    /**
     * The new version to use in the <i>semantic-version.properties</i> in the repackaged {@code HederaNode.jar}.
     */
    private final SemanticVersion newVersion;
    /**
     * The path at which to create the upgrade zip for file {@code 0.0.150}.
     */
    private final Path upgradeZipLoc;

    @Nullable
    private final NodeId nodeIdToRemove;

    @Nullable
    private final Path newSettingsLoc;

    public BuildUpgradeZipOp(
            @NonNull final Path runningJarLoc,
            @NonNull final SemanticVersion newVersion,
            @NonNull final Path upgradeZipLoc) {
        this(runningJarLoc, newVersion, upgradeZipLoc, null, null);
    }

    public BuildUpgradeZipOp(
            @NonNull final Path runningJarLoc,
            @NonNull final SemanticVersion newVersion,
            @NonNull final Path upgradeZipLoc,
            @NonNull NodeId nodeIdToRemove) {
        this(runningJarLoc, newVersion, upgradeZipLoc, requireNonNull(nodeIdToRemove), null);
    }

    private BuildUpgradeZipOp(
            @NonNull final Path runningJarLoc,
            @NonNull final SemanticVersion newVersion,
            @NonNull final Path upgradeZipLoc,
            @Nullable NodeId nodeIdToRemove,
            @Nullable Path newSettingsLoc) {
        this.runningJarLoc = requireNonNull(runningJarLoc);
        this.newVersion = requireNonNull(newVersion);
        this.upgradeZipLoc = requireNonNull(upgradeZipLoc);
        this.nodeIdToRemove = nodeIdToRemove;
        this.newSettingsLoc = newSettingsLoc;
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        if (nodeIdToRemove != null) {
            assertTargetsSubProcessNetwork(spec);
        }
        try {
            repackageJarWithNewServicesVersion(runningJarLoc, newVersion);
            zipDirectory(upgradeZipLoc);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return false;
    }

    private void assertTargetsSubProcessNetwork(@NonNull final HapiSpec spec) {
        if (!(spec.targetNetworkOrThrow() instanceof SubProcessNetwork)) {
            throw new IllegalStateException("Can only build upgrade zip for a SubProcessNetwork");
        }
    }

    private static void repackageJarWithNewServicesVersion(
            @NonNull final Path initialJarPath, @NonNull final SemanticVersion overrideVersion) throws IOException {
        final var extractionPath = Files.createDirectories(EXPLODED_ZIP_PATH);
        try (final var jarFile =
                new JarFile(initialJarPath.normalize().toAbsolutePath().toFile())) {
            extractTo(jarFile, extractionPath);
            overwriteProperties(
                    extractionPath.resolve(SEM_VER_FILE), SERVICES_VERSION_PROP, HapiUtils.toString(overrideVersion).substring(1));
            Files.createDirectories(UPGRADE_ZIP_PATH);
            try (final var jos = new JarOutputStream(new FileOutputStream(NEW_JAR_PATH.toFile()))) {
                try (final var files = Files.walk(extractionPath)) {
                    files.filter(path -> !Files.isDirectory(path)).forEach(path -> {
                        final var entryName =
                                extractionPath.relativize(path).toString().replace(File.separatorChar, '/');
                        try (final var in = Files.newInputStream(path)) {
                            jos.putNextEntry(new JarEntry(entryName));
                            in.transferTo(jos);
                            jos.closeEntry();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                }
            }
        } finally {
            try (final var files = Files.walk(extractionPath)) {
                files.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }

    private static void zipDirectory(@NonNull final Path zipPath) throws IOException {
        try (final var zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            try (final var files = Files.walk(UPGRADE_ZIP_PATH)) {
                files.filter(path -> !Files.isDirectory(path)).forEach(path -> {
                    final var zipEntry =
                            new ZipEntry(UPGRADE_ZIP_PATH.relativize(path).toString());
                    try {
                        zos.putNextEntry(zipEntry);
                        Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
    }

    private static void overwriteProperties(
            @NonNull final Path path, @NonNull final String key, @NonNull final String value) throws IOException {
        final var properties = new Properties();
        try (final var in = Files.newInputStream(path)) {
            properties.load(in);
        }
        properties.setProperty(key, value);
        try (final var out = Files.newOutputStream(path)) {
            properties.store(out, null);
        }
    }

    private static void extractTo(@NonNull final JarFile jarFile, @NonNull final Path extractionPath) {
        jarFile.stream().forEach(entry -> {
            final var entryPath = extractionPath.resolve(entry.getName());
            if (entry.isDirectory()) {
                try {
                    Files.createDirectories(entryPath);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            } else {
                try (final var in = jarFile.getInputStream(entry)) {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(in, entryPath);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
    }
}
