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

package com.hedera.services.cli.block.test;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.services.cli.block.BlockRead;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

@ExtendWith(MockitoExtension.class)
class BlockReadTest {
    private static final String BLOCK_1_FILENAME = "1.blk.gz";
    private static final String COMPRESSED_BLOCK_1_FILEPATH =
            "com/hedera/services/cli/block/test/rsc/" + BLOCK_1_FILENAME;
    private static final String UNCOMPRESSED_BLOCK_2_FILENAME = "2.blk";
    private static final String COMPRESSED_BLOCK_2_FILENAME = UNCOMPRESSED_BLOCK_2_FILENAME + ".gz";
    private static final String COMPRESSED_BLOCK_2_FILEPATH =
            "com/hedera/services/cli/block/test/rsc/block-2-compressed/" + COMPRESSED_BLOCK_2_FILENAME;
    private static final String UNCOMPRESSED_BLOCK_2_FILEPATH =
            "com/hedera/services/cli/block/test/rsc/block-2-uncompressed/" + UNCOMPRESSED_BLOCK_2_FILENAME;

    private static final String WHITESPACE_REGEX = "\\n{2,}";

    @Mock
    private CommandLine.Model.CommandSpec spec;

    @TempDir
    private Path tmpDir;

    @Test
    void callWithEmptyParamsThrows() {
        given(spec.commandLine()).willReturn(mock(CommandLine.class));

        Assertions.assertThatNoException()
                .isThrownBy(() -> BlockRead.forTest(spec, List.of("value1"), List.of("value2"), System.out)
                        .call());

        Assertions.assertThatThrownBy(() -> {
                    //noinspection DataFlowIssue
                    BlockRead.forTest(null, List.of(), List.of(), System.out).call();
                })
                .isInstanceOf(NullPointerException.class);

        Assertions.assertThatNoException()
                .isThrownBy(() -> BlockRead.forTest(spec, null, List.of("value2"), System.out));
        Assertions.assertThatNoException().isThrownBy(() -> BlockRead.forTest(spec, List.of("value1"), null, System.out)
                .call());

        Assertions.assertThatThrownBy(
                        () -> BlockRead.forTest(spec, null, null, System.out).call())
                .isInstanceOf(CommandLine.ParameterException.class);
        Assertions.assertThatThrownBy(() ->
                        BlockRead.forTest(spec, null, List.of(), System.out).call())
                .isInstanceOf(CommandLine.ParameterException.class);
        Assertions.assertThatThrownBy(() ->
                        BlockRead.forTest(spec, List.of(), null, System.out).call())
                .isInstanceOf(CommandLine.ParameterException.class);
        Assertions.assertThatThrownBy(() -> BlockRead.forTest(spec, List.of(), List.of(), System.out)
                        .call())
                .isInstanceOf(CommandLine.ParameterException.class);
    }

    @Test
    void readsSingleValidCompressedFile() throws Exception {
        final var actual = doCommandFileOutput("readsSingleValidCompressedFile", COMPRESSED_BLOCK_1_FILEPATH);

        final var parts = actual.split(WHITESPACE_REGEX);
        Assertions.assertThat(parts[1]).contains(BLOCK_1_JSON);
        Assertions.assertThat(parts[2]).isEqualTo(numFilesString(1));
    }

    @Test
    void readsSingleValidUncompressedFile() throws Exception {
        final var actual = doCommandFileOutput("readsSingleValidUncompressedFile", UNCOMPRESSED_BLOCK_2_FILEPATH);

        final var parts = actual.split(WHITESPACE_REGEX);
        Assertions.assertThat(parts[1]).isEqualTo(BLOCK_2_JSON_UNCOMPRESSED);
        Assertions.assertThat(parts[2]).isEqualTo(numFilesString(1));
    }

    @Test
    void readsSingleInvalidFile() throws Exception {
        final var writeLocation = tmpDir.resolve("output-" + "readsSingleInvalidFile" + "_" + Instant.now() + ".txt");
        String actual;
        try (final var ps = new PrintStream(writeLocation.toFile())) {
            final var subject = BlockRead.forTest(spec, List.of("nonexistent.blk"), null, ps);
            final var result = subject.call();
            Assertions.assertThat(result).isOne();

            final var fileText = Files.readString(writeLocation).trim();
            actual = eraseFilePrefixes(fileText);
        }

        Assertions.assertThat(actual)
                .contains("Error trying to read")
                .contains("nonexistent.blk")
                .contains("No block files were read")
                .doesNotContain("Block '")
                .doesNotContain("Successfully");
    }

    @Test
    void readsMultipleValidFiles() throws Exception {
        final var actual = doCommandFileOutput(
                "readsMultipleValidFiles", COMPRESSED_BLOCK_1_FILEPATH, COMPRESSED_BLOCK_2_FILEPATH);

        // file 1:
        final var parts = actual.split(WHITESPACE_REGEX);
        Assertions.assertThat(parts[1]).isEqualTo(BLOCK_1_JSON);

        // file 2:
        Assertions.assertThat(parts[2]).isEqualTo(BLOCK_2_JSON_COMPRESSED);

        // summary text:
        Assertions.assertThat(parts[3]).isEqualTo(numFilesString(2));
    }

    @Test
    void readsValidAndNonexistentFiles() throws Exception {
        final var writeLocation =
                tmpDir.resolve("output-" + "readsValidAndNonexistentFiles" + "_" + Instant.now() + ".txt");
        String actual;
        try (final var ps = new PrintStream(writeLocation.toFile())) {
            final var allFileInputs = fullPathsForFileInputs(COMPRESSED_BLOCK_1_FILEPATH);
            allFileInputs.add("nonexistent.blk");
            final var subject = BlockRead.forTest(spec, allFileInputs, null, ps);
            final var result = subject.call();
            Assertions.assertThat(result).isOne();

            final var fileText = Files.readString(writeLocation).trim();
            actual = eraseFilePrefixes(fileText);
        }

        Assertions.assertThat(actual).contains("Error trying to read 'nonexistent.blk'");
        final var parts = actual.split(WHITESPACE_REGEX);
        Assertions.assertThat(parts[0]).contains("Failed to read 1 block file(s)");
        Assertions.assertThat(parts[1]).contains(BLOCK_1_JSON);
        Assertions.assertThat(parts[2]).contains(numFilesString(1));
    }

    @Test
    void readsSingleValidDir() throws Exception {
        final var actual = doCommandDirOutput("readsSingleValidDir", InputDirType.COMPRESSED_2);
        // Since we have other tests covering the actual block content, we just check the summary text here.
        Assertions.assertThat(actual.trim()).endsWith(numFilesString(1));
    }

    @Test
    void readsSingleInvalidDir() throws Exception {
        final var writeLocation = tmpDir.resolve("output-" + "readsSingleInvalidDir" + "_" + Instant.now() + ".txt");
        String actual;
        try (final var ps = new PrintStream(writeLocation.toFile())) {
            final var subject = BlockRead.forTest(spec, null, List.of("nonexistent-dir"), ps);
            final var result = subject.call();
            Assertions.assertThat(result).isOne();
        }

        final var fileText = Files.readString(writeLocation).trim();
        actual = eraseFilePrefixes(fileText);

        Assertions.assertThat(actual)
                .contains("Error reading directory 'nonexistent-dir'")
                .doesNotContain("Block '")
                .doesNotContain("Successfully");
    }

    @Test
    void readsMultipleValidDirs() throws Exception {
        final var actual =
                doCommandDirOutput("readsMultipleValidDirs", InputDirType.COMPRESSED_2, InputDirType.UNCOMPRESSED_2);
        // Since we have other tests covering the actual block content, we just check the summary text here.
        Assertions.assertThat(actual.trim()).endsWith(numFilesString(2));
    }

    @Test
    void readsValidAndInvalidDirs() throws Exception {
        final var writeLocation = tmpDir.resolve("output-" + "readsSingleInvalidDir" + "_" + Instant.now() + ".txt");
        String actual;
        try (final var ps = new PrintStream(writeLocation.toFile())) {
            final var allDirInputs = fullPathsForDirInputs(InputDirType.COMPRESSED_2);
            allDirInputs.add("nonexistent-dir");
            final var subject = BlockRead.forTest(spec, null, allDirInputs, ps);
            final var result = subject.call();
            Assertions.assertThat(result).isOne();

            final var fileText = Files.readString(writeLocation).trim();
            actual = eraseFilePrefixes(fileText);
        }

        final var parts = actual.split(WHITESPACE_REGEX);
        Assertions.assertThat(parts[0]).contains("Error reading directory 'nonexistent-dir': nonexistent-dir");
        Assertions.assertThat(parts[1]).contains(BLOCK_2_JSON_COMPRESSED);
        Assertions.assertThat(parts[2]).contains(numFilesString(1));
    }

    @Test
    void readsMultipleValidFilesAndValidDirs() throws Exception {
        final var fileInputs = fullPathsForFileInputs(COMPRESSED_BLOCK_1_FILEPATH, COMPRESSED_BLOCK_2_FILEPATH);
        final var dirInputs = fullPathsForDirInputs(InputDirType.UNCOMPRESSED_2);
        final var actual = doCommandWrite("readsMultipleValidFilesAndValidDirs", fileInputs, dirInputs);
        // Since we have other tests covering the actual block content, we just check the summary text here.
        Assertions.assertThat(actual.trim()).endsWith(numFilesString(3));
    }

    @Test
    void readsDirRecursively() throws Exception {
        final var actual = doCommandDirOutput("readsDirRecursively", InputDirType.ROOT);
        // Since we have other tests covering the actual block content, we just check the summary text here.
        Assertions.assertThat(actual.trim()).endsWith(numFilesString(3));
    }

    @Test
    void onlyReadsFileOnceWhenSpecifiedTwice() throws Exception {
        final var actual = doCommandFileOutput(
                "onlyReadsFileOnceWhenSpecifiedTwice", COMPRESSED_BLOCK_1_FILEPATH, COMPRESSED_BLOCK_1_FILEPATH);
        // Since we have other tests covering the actual block content, we just check the summary text here.
        Assertions.assertThat(actual.trim()).endsWith(numFilesString(1));
    }

    @Test
    void onlyReadsDirOnceWhenSpecifiedTwice() throws Exception {
        final var actual =
                doCommandDirOutput("onlyReadsDirOnceWhenSpecifiedTwice", InputDirType.ROOT, InputDirType.ROOT);
        // Since we have other tests covering the actual block content, we just check the summary text here.
        Assertions.assertThat(actual.trim()).endsWith(numFilesString(3));
    }

    private String doCommandFileOutput(final String callingFuncName, final String... inputFiles) throws Exception {
        final var fullPathInputs = fullPathsForFileInputs(inputFiles);

        return doCommandWrite(callingFuncName, fullPathInputs, null);
    }

    private String doCommandDirOutput(final String callingFuncName, final InputDirType... inputTypes) throws Exception {
        // Precondition:
        final var fullPathInputs = fullPathsForDirInputs(inputTypes);

        return doCommandWrite(callingFuncName, null, fullPathInputs);
    }

    private String doCommandWrite(final String callingFuncName, final List<String> files, final List<String> dirs)
            throws Exception {
        final var writeLocation = tmpDir.resolve("output-" + callingFuncName + "_" + Instant.now() + ".txt");
        try (final var ps = new PrintStream(writeLocation.toFile())) {
            final var subject = BlockRead.forTest(spec, files, dirs, ps);
            final var result = subject.call();
            Assertions.assertThat(result).isZero();

            final var fileText = Files.readString(writeLocation).trim();
            return eraseFilePrefixes(fileText);
        }
    }

    private List<String> fullPathsForFileInputs(final String... inputFiles) {
        final var fullPathInputs = new ArrayList<String>();
        for (final var inputFile : inputFiles) {
            // Precondition:
            final var blockInputFile = BlockReadTest.class.getClassLoader().getResource(inputFile);
            Assertions.assertThat(blockInputFile).isNotNull();

            fullPathInputs.add(blockInputFile.getPath());
        }

        return fullPathInputs;
    }

    private List<String> fullPathsForDirInputs(final InputDirType... inputTypes) throws IOException {
        final var fullPathInputs = new ArrayList<String>();
        for (final var inputType : inputTypes) {
            final var inputPath = inputType.path();
            if (inputPath.isEmpty()) {
                continue;
            }
            // Compute the full path of the directory, so we can pass it to the command:
            final var blockInputFile = BlockReadTest.class.getClassLoader().getResources(inputPath);
            Assertions.assertThat(blockInputFile).isNotNull();
            // We only care about the first element
            fullPathInputs.add(blockInputFile.nextElement().getPath());
        }

        return fullPathInputs;
    }

    /**
     * This enum exists purely to facilitate testing with different directories, including a root
     * that contains all the .blk files
     */
    private enum InputDirType {
        NONE,
        ROOT, // contains all three block files
        COMPRESSED_2, // contains only 2.blk.gz
        UNCOMPRESSED_2; // contains only 2.blk

        public String path() {
            final var relativeRoot = "com/hedera/services/cli/block/test/rsc";
            return switch (this) {
                case NONE -> "";
                case ROOT -> relativeRoot;
                case COMPRESSED_2 -> relativeRoot + "/block-2-compressed";
                case UNCOMPRESSED_2 -> relativeRoot + "/block-2-uncompressed";
            };
        }
    }

    private static final String BLOCK_FILES_PRINTED_TEMPLATE = "Successfully output {{num}} block file(s)";

    private static @NonNull String numFilesString(final int expectedNumFiles) {
        return BLOCK_FILES_PRINTED_TEMPLATE.replace("{{num}}", "" + expectedNumFiles);
    }

    private String eraseFilePrefixes(final String actual) {
        final var template = "'{{file-sep}}?.+{{file-sep}}hedera-node".replace("{{file-sep}}", File.separator);
        return actual.replaceAll(template, "'hedera-node");
    }

    private static final String BLOCK_1_JSON =
            """
            Block 'hedera-node/cli-clients/build/resources/test/com/hedera/services/cli/block/test/rsc/{{filename}}'----------------
            {
              "items": [{
                "header": {
                  "hapiProtoVersion": {
                    "major": 7
                  },
                  "previousBlockProofHash": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                  "addressBookVersion": {
                    "major": 7
                  }
                }
              }, {
                "startEvent": {
                  "softwareVersion": {
                    "minor": 51
                  },
                  "selfParent": {
                    "creatorId": "-1",
                    "generation": "-1",
                    "birthRound": "-1"
                  },
                  "otherParents": [{
                    "creatorId": "-1",
                    "generation": "-1",
                    "birthRound": "-1"
                  }],
                  "timeCreated": {
                    "seconds": "1718404460",
                    "nanos": 229636000
                  },
                  "hash": "WjB5J0sOgmZvthzr2LODtrmK82xlcR0AyTUx5MLyXAlxN4ECKATVPjsm3rAnIJ+b",
                  "signature": "lPLMsX7ZqoNJyeNoRKm/syoTRQHH30BIJZMgdBoQotEsXHFQ3Oihu3vo/xN658NzKENAV/Zcd6V2G2XJdtIod3S8oiLJlbJD+p00NW6+FmYlz+y+9CdUfYNtzmLbqlZrL7TjFmvb6b/r6KvBiGDiIUWkWJy0/Myn10GnARuTvWIaKTd450H7oBEkNJIL3xGnSxsPOvSBscSy900fWCnxTf3fFjmGX589KPWJl6d1GmWprfHSYOrXyTnDlv65YS+li020wbvpOIhqRqmVVTGIkR5I12XyqYD0Ssx9lQcX2X7hrQ6lnWZAc57CvWZePCkBM7NIgQMo+KI6NVmD6qKWwtqcuvoiwdO3I9vM5M4bIJdmXLfSzOxvAe13VP3jw4vGhvF3Ptv2uYVOj2ziNjG5dkdFLKVM0vxHuS9C57mfWl2cuspcwgy54zdTd9929uTC7/0uBbXpJyQHUjzpSAsgAiv1CgLEuraJv/R2C5cCjvWpoQy/XQiVuitW7zI5zdTK",
                  "consensusData": {
                    "consensusTimestamp": {
                      "seconds": "1718404460",
                      "nanos": 229636000
                    },
                    "round": "1"
                  }
                }
              }]
            }
            ----------------"""
                    // Use the system file separator to avoid issues with paths
                    .replace("/", File.separator)
                    .replace("{{filename}}", BLOCK_1_FILENAME);

    private static final String BLOCK_2_JSON_COMPRESSED =
            """
            Block 'hedera-node/cli-clients/build/resources/test/com/hedera/services/cli/block/test/rsc/block-2-compressed/{{filename}}'----------------
            {
              "items": [{
                "header": {
                  "hapiProtoVersion": {
                    "major": 7
                  },
                  "number": "1",
                  "previousBlockProofHash": "+yn7QOYCDH1b8O4cDrRaEVF2PWKjR/Ig9/Jj7s+XLjtcZHndaeuJPp7rCi5mbWxP",
                  "addressBookVersion": {
                    "major": 7
                  }
                }
              }, {
                "startEvent": {
                  "softwareVersion": {
                    "minor": 51
                  },
                  "generation": "1",
                  "birthRound": "1",
                  "selfParent": {
                    "hash": "gMZl3pEbLBvf1Hk/yVVmFBlsrwKvQceKVYe25cIh72g+5IYE8AWgZPD3uAyaNESI"
                  },
                  "otherParents": [{
                    "hash": "gMZl3pEbLBvf1Hk/yVVmFBlsrwKvQceKVYe25cIh72g+5IYE8AWgZPD3uAyaNESI"
                  }],
                  "timeCreated": {
                    "seconds": "1718083860",
                    "nanos": 169964000
                  },
                  "hash": "rZHHge0v1QI30e2dFdxDXR5egCnvoEpERhBSN8bNN+sm6ijQrOYL9fh1jT3Hbggo",
                  "signature": "p+sk6WGLMuIsa4MLYvGk/V9xjovg2ZspNbDjRS5bV5IWdB/1Pb5pUm19IeTlFdirSLT8CjpEPpoDE75v/QQ23Aws1N7uAqXuawizV0SdnOKQjndNHT5w4lIP6LlT8z66qQBdz4LP8Zi7ZF/7rw83s0k2b3IjAheTokfTK/cDmEnHQpZL/QLoZ7Gv8C4Dpi09OJXyAeIRptua549tcsQBJs4ddcMP/vrYD4/3S4SZTe7al6JwYOBxP9tHkyFP4Q0Pv2VcG9Niba8PXa4LNhj/zjrSeffgEF+PMV/qLvMHMFrYot4UPUm+FS9nC49sbk7oyzWg1p7WrysCCzVQ6hEpiMV4SmwKbBorZXeBfnP/rXdq7esq6vCBkkwzxtYAitOOHOeDORKoO07GODDA9tK6p4HQ42ebhFerIyHArfwZ4RNNp8lf8F8+4MYdOZidJm0XDE5meyhdwXMDZLl2Rohu/VfGermpPq3ZvIjbi3dkbgJM5OwCt2Py/yxovv1RgntN",
                  "consensusData": {
                    "consensusTimestamp": {
                      "seconds": "1718083860",
                      "nanos": 169964000
                    },
                    "round": "2",
                    "order": "1"
                  }
                }
              }]
            }
            ----------------"""
                    .replace("{{filename}}", COMPRESSED_BLOCK_2_FILENAME);

    private static final String BLOCK_2_JSON_UNCOMPRESSED = BLOCK_2_JSON_COMPRESSED
            .replace("block-2-compressed", "block-2-uncompressed")
            .replace(COMPRESSED_BLOCK_2_FILENAME, UNCOMPRESSED_BLOCK_2_FILENAME);
}
