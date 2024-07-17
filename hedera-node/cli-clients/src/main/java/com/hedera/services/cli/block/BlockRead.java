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

package com.hedera.services.cli.block;

import static java.util.Objects.requireNonNull;
import static picocli.CommandLine.Option;
import static picocli.CommandLine.ParameterException;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.block.stream.Block;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;
import picocli.CommandLine;

@CommandLine.Command(name = "read", mixinStandardHelpOptions = true, description = "Reads block stream files (.blk)")
@SubcommandOf(BlockCommand.class)
public class BlockRead extends AbstractCommand {
    private static final String BLOCK_FILE_EXTENSION = ".blk";
    private static final String COMPRESSED_FILE_EXTENSION = ".gz";
    private static final int CHUNK_SIZE = 20000;
    private static final String DELIMITER = "----------------" + System.lineSeparator();

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;
    // This is a setter for the picocli library, and needs to be public
    public void setSpec(@NonNull final CommandLine.Model.CommandSpec spec) {
        this.spec = requireNonNull(spec);
    }

    @Option(
            names = {"-f", "--file"},
            paramLabel = "single block file to read",
            arity = "0..*")
    List<String> files;
    // This is a setter for the picocli library, and needs to be public
    public void setFiles(@Nullable List<String> files) {
        this.files = files;
    }

    @Option(
            names = {"-d", "--dir"},
            paramLabel = "directory of block files to read (recursive)",
            arity = "0..*")
    List<String> dirs;
    // This is a setter for the picocli library, and needs to be public
    public void setDirs(@Nullable List<String> dirs) {
        this.dirs = dirs;
    }

    private PrintStream output = System.out;
    private PrintStream errOutput = System.err;

    // This variable is used to determine the return code of the command
    private boolean errorEncountered = false;

    @VisibleForTesting
    public static BlockRead forTest(
            @NonNull final CommandLine.Model.CommandSpec spec,
            @Nullable final List<String> files,
            @Nullable final List<String> dirs,
            @NonNull final PrintStream out) {
        final var subject = new BlockRead();
        subject.setSpec(spec);
        subject.setFiles(files);
        subject.setDirs(dirs);
        subject.setOutput(out);
        subject.setErrOutput(out);
        return subject;
    }

    @Override
    public Integer call() throws Exception {
        throwOnInvalidCommandLine();

        final List<String> allFiles;
        try {
            allFiles = gatherFilesOrThrow();
        } catch (NoSuchFileException e) {
            errOutput.println("Error reading nonexistent file: " + e.getMessage());
            // This isn't really necessary since the purpose of errorEncountered is to return the correct return code,
            // but it's here for clarity
            errorEncountered = true;
            return 1;
        } catch (IOException e) {
            errOutput.println("Error reading block files: " + e.getMessage());
            // This isn't really necessary since the purpose of errorEncountered is to return the correct return code,
            // but it's here for clarity
            errorEncountered = true;
            return 1;
        }

        final var sb = new StringBuilder();
        int readFiles = 0;
        int erroredFiles = 0;
        for (final var filename : allFiles) {
            output.println("Reading '" + filename + "'");

            final var bytes = readBlockFile(filename);
            if (bytes.length == 0) {
                erroredFiles++;
                continue;
            }
            final var result = Block.PROTOBUF.parse(Bytes.wrap(bytes));
            sb.append(System.lineSeparator());
            sb.append("Block '").append(filename).append("'");
            sb.append(DELIMITER);
            sb.append(Block.JSON.toJSON(result));
            sb.append(System.lineSeparator());
            sb.append(DELIMITER);
            readFiles++;
        }

        if (erroredFiles > 0) {
            errOutput.println("Failed to read " + erroredFiles + " block file(s)");
            errorEncountered = true;
        }
        if (readFiles == 0) {
            errOutput.println("No block files were read");
            errorEncountered = true;
        }
        if (readFiles > 0) {
            output.println(sb);
            output.println("Successfully output " + readFiles + " block file(s)");
        }

        return errorEncountered ? 1 : 0;
    }

    @VisibleForTesting
    // This setter isn't publicly visible because it's not needed by picocli, only for testing
    void setOutput(PrintStream output) {
        this.output = output;
    }

    @VisibleForTesting
    // This setter isn't publicly visible because it's not needed by picocli, only for testing
    void setErrOutput(PrintStream errOutput) {
        this.errOutput = errOutput;
    }

    private List<String> gatherFilesOrThrow() throws IOException {
        // We'll use a set to ensure uniqueness
        final var allFiles = new TreeSet<String>();

        // Look at given files first
        if (files == null) {
            files = new ArrayList<>();
        }
        if (!files.isEmpty()) {
            allFiles.addAll(files.stream().distinct().toList());
        }

        if (dirs == null) {
            dirs = new ArrayList<>();
        }
        // If there are no files or dirs, insert the current dir
        if (allFiles.isEmpty() && dirs.isEmpty()) {
            dirs.add(".");
        }
        for (final String dir : dirs) {
            try (var stream = Files.walk(Paths.get(dir))) {
                stream.filter(Files::isRegularFile)
                        .filter(f -> isBlockFile(f.toString()))
                        .distinct()
                        // Replace any leading or intermittent "./" or "../" in the path
                        .map(f -> f.toString().replaceFirst("\\.+/", ""))
                        .forEach(allFiles::add);
            } catch (IOException e) {
                errOutput.println("Error reading directory '" + dir + "': " + e.getMessage());
                errorEncountered = true;
            }
        }

        // If we still don't have any files to read, give up
        if (allFiles.isEmpty()) {
            errorEncountered = true;
        }

        return allFiles.stream().toList();
    }

    private void throwOnInvalidCommandLine() {
        if ((files == null || files.isEmpty()) && (dirs == null || dirs.isEmpty())) {
            throw new ParameterException(spec.commandLine(), "Please specify at least one file or directory");
        }
    }

    private byte[] readBlockFile(final String fileLoc) throws IOException {
        InputStream fin = null;
        try (final var bos = new ByteArrayOutputStream()) {
            fin = new FileInputStream(fileLoc);
            if (fileLoc.endsWith(COMPRESSED_FILE_EXTENSION)) {
                fin = new GZIPInputStream(fin);
            }

            int len;
            final var buffer = new byte[CHUNK_SIZE];
            while ((len = fin.read(buffer)) > 0) {
                bos.write(buffer, 0, len);
            }

            return bos.toByteArray();
        } catch (IOException e) {
            errOutput.println("Error trying to read '" + fileLoc + "': " + e.getMessage());
        } finally {
            if (fin != null) {
                fin.close();
            }
        }

        return new byte[0];
    }

    private static boolean isBlockFile(final String filename) {
        return filename.endsWith(BLOCK_FILE_EXTENSION)
                || filename.endsWith(BLOCK_FILE_EXTENSION + COMPRESSED_FILE_EXTENSION);
    }
}
