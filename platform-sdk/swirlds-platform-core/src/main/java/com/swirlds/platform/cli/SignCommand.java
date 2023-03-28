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
        pathMustExist(keyFilePath.toAbsolutePath());

        this.keyFilePath = keyFilePath;
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
        for (final Path path : pathsToSign) {
            pathMustExist(path.toAbsolutePath());
        }

        this.pathsToSign = pathsToSign;
    }

    @CommandLine.Option(
            names = {"-d", "--destination-directory"},
            description = "Specify the destination directory where signature files will be generated."
                    + "If not specified, a signature file will be generated in the same directory as the source file")
    private void setDestinationDirectory(@NonNull final Path destinationDirectory) {
        this.destinationDirectory = destinationDirectory.toAbsolutePath();
    }

    @Override
    public Integer call() {
        keyPair = FileSigningUtils.loadPfxKey(keyFilePath, keyFilePassword, keyAlias);

        for (final Path path : pathsToSign) {
            if (Files.isDirectory(path)) {
                signAllFilesInDirectory(path);
            } else {
                generateSignatureFile(destinationDirectory, path, keyPair);
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
     */
    public abstract void generateSignatureFile(
            @Nullable final Path destinationDirectory, @NonNull final Path fileToSign, @NonNull final KeyPair keyPair);

    /**
     * Check if a file is supported by this command
     *
     * @param path the path to the file to check
     * @return true if the file is supported, false otherwise
     */
    public abstract boolean isFileSupported(@NonNull final Path path);

    /**
     * Sign all files in a directory, recursively
     *
     * @param directoryPath the path to the directory to sign
     */
    private void signAllFilesInDirectory(@NonNull final Path directoryPath) {
        try (final Stream<Path> stream = Files.walk(directoryPath)) {
            stream.filter(this::isFileSupported)
                    .forEach(filePath -> generateSignatureFile(destinationDirectory, filePath, keyPair));
        } catch (final IOException e) {
            throw new RuntimeException("Failed to list files in directory: " + directoryPath);
        }
    }
}
