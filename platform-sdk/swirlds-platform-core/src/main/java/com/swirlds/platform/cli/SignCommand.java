/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.cli;

import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.platform.util.FileSigningUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;
import java.util.stream.Stream;
import picocli.CommandLine;

/**
 * An abstract command type for generating signature files
 */
public abstract class SignCommand extends AbstractCommand {
    /**
     * The paths to the sources that signature files will be generated for. Can contain individual files, as well as
     * directories
     * <p>
     * If an element of this list is a directory, then all files in that directory will be signed, recursively
     * <p>
     * Defaults to the current working directory
     */
    private List<Path> pathsToSign = List.of(FileUtils.getAbsolutePath());

    /**
     * The directory where signature files will be generated. Defaults to in-place signatures (null)
     */
    @Nullable
    private Path destinationDirectory = null;

    /**
     * The path to the key file to use to generate signatures
     */
    private Path keyFilePath;

    /**
     * The password to the key file
     */
    private String keyFilePassword;

    /**
     * The alias of the key in the key file
     */
    private String keyAlias;

    /**
     * The key pair to use to generate signatures
     */
    private KeyPair keyPair;

    @CommandLine.Parameters(description = "The path to the key file to use to generate signatures", index = "0")
    private void setKeyFile(@NonNull final Path keyFilePath) {
        this.keyFilePath = pathMustExist(keyFilePath.toAbsolutePath().normalize());
    }

    @CommandLine.Parameters(description = "The password to the key file", index = "1")
    private void setKeyFilePassword(@NonNull final String keyFilePassword) {
        this.keyFilePassword = keyFilePassword;
    }

    @CommandLine.Parameters(description = "The alias of the key in the key file", index = "2")
    private void setKeyAlias(@NonNull final String keyAlias) {
        this.keyAlias = keyAlias;
    }

    @CommandLine.Option(
            names = {"-p", "--paths-to-sign"},
            description = "The paths to what will be signed. Can contain single files, as well as directories."
                    + "Defaults to the current working directory")
    private void setPathsToSign(@NonNull final List<Path> pathsToSign) {
        this.pathsToSign = pathsToSign.stream()
                .map((path -> pathMustExist(path.toAbsolutePath().normalize())))
                .toList();
    }

    @CommandLine.Option(
            names = {"-d", "--destination-directory"},
            description = "Specify the destination directory where signature files will be generated."
                    + "If not specified, a signature file will be generated in the same directory as the source file")
    private void setDestinationDirectory(@NonNull final Path destinationDirectory) {
        this.destinationDirectory = destinationDirectory.toAbsolutePath().normalize();
    }

    @Override
    public Integer call() {
        keyPair = FileSigningUtils.loadPfxKey(keyFilePath, keyFilePassword, keyAlias);

        for (final Path path : pathsToSign) {
            if (Files.isDirectory(path)) {
                signAllFilesInDirectory(path);
            } else {
                sign(path);
            }
        }

        return 0;
    }

    /**
     * Generate a signature file for a single source file
     *
     * @param destinationDirectory the directory where the signature file will be generated. null means signatures will
     *                             be generated in the same directory as the source files
     * @param fileToSign           the file to generate a signature file for
     * @param keyPair              the key pair to use to generate the signature
     * @return true if the signature file was generated successfully, false otherwise
     */
    public abstract boolean generateSignatureFile(
            @Nullable final Path destinationDirectory, @NonNull final Path fileToSign, @NonNull final KeyPair keyPair);

    /**
     * Check if a file is supported by this command
     *
     * @param path the path to the file to check
     * @return true if the file is supported, false otherwise
     */
    public abstract boolean isFileSupported(@NonNull final Path path);

    /**
     * Perform necessary tasks to sign a file. In all cases, generates a signature file via
     * {@link #generateSignatureFile}
     * <p>
     * If a destinationDirectory has been specified, the source file will additionally be copied to the destination
     * directory
     *
     * @param fileToSign the file to generate a signature file for
     */
    private void sign(@NonNull final Path fileToSign) {
        // if signature generation fails, don't continue
        if (!generateSignatureFile(destinationDirectory, fileToSign, keyPair)) {
            return;
        }

        // if destinationDirectory is null, then we are generating in-place signatures. No need to copy source files
        if (destinationDirectory == null) {
            return;
        }

        try {
            Files.copy(fileToSign, destinationDirectory.resolve(fileToSign.getFileName()));
        } catch (final IOException e) {
            System.err.println("Failed to copy source file " + fileToSign.getFileName() + " to destination directory "
                    + destinationDirectory + ". Exception: " + e);
        }
    }

    /**
     * Sign all files in a directory, recursively
     *
     * @param directoryPath the path to the directory to sign
     */
    private void signAllFilesInDirectory(@NonNull final Path directoryPath) {
        try (final Stream<Path> stream = Files.walk(directoryPath)) {
            stream.filter(this::isFileSupported).forEach(this::sign);
        } catch (final IOException e) {
            throw new RuntimeException("Failed to list files in directory: " + directoryPath);
        }
    }
}
