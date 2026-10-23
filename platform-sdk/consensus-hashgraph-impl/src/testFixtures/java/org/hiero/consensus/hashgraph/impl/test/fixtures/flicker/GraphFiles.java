// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.hashgraph.impl.test.fixtures.flicker;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads a {@code .graph} file that ships beside this class.
 * <p>
 * Fixtures live in test fixtures rather than in a test source set so that anything depending on this module can read
 * them — the test that asserts on a graph, and the viewer an engineer opens when that test fails, are not the same
 * program and should not need two copies of the file.
 */
public final class GraphFiles {

    private GraphFiles() {}

    /**
     * Load a graph by file name.
     *
     * @param name the file name, such as {@code scn-001.graph}
     * @return the parsed graph
     * @throws IllegalArgumentException if no such file ships with this module
     * @throws GraphParseException      if the file is not a well-formed graph
     */
    @NonNull
    public static FlickerGraph load(@NonNull final String name) {
        return GraphParser.parse(read(name));
    }

    /**
     * Read a graph file without parsing it.
     *
     * @param name the file name, such as {@code scn-001.graph}
     * @return the file's text
     * @throws IllegalArgumentException if no such file ships with this module
     */
    @NonNull
    private static String read(@NonNull final String name) {
        try (final InputStream in = GraphFiles.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalArgumentException(
                        "no graph named '%s' beside %s".formatted(name, GraphFiles.class.getName()));
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException("could not read graph '%s'".formatted(name), e);
        }
    }
}
