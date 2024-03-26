/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.statedumpers.utils;

import com.swirlds.common.AutoCloseableNonThrowing;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Holds a file that you can write to, and implements obvious writing methods.  Key features are 1) throws
 * `UncheckedIOException` instead of `IOException` and 2) keeps track of how many characters are written.
 */
public class Writer implements AutoCloseableNonThrowing {
    public static final String FIELD_SEPARATOR = ";";
    private final FileWriter fw;
    private final BufferedWriter bw;
    private int size;

    public Writer(@NonNull final Path path) {
        try {
            fw = new FileWriter(path.toFile(), StandardCharsets.UTF_8);
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
        bw = new BufferedWriter(fw);
    }

    public void write(@NonNull final Object o) {
        final var s = o.toString();
        try {
            bw.write(s);
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
        size += s.length();
    }

    public void write(@NonNull final String format, @NonNull final Object... os) {
        final var s = format.formatted(os);
        try {
            bw.write(s);
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
        size += s.length();
    }

    public void writeln(@NonNull final Object o) {
        write(o);
        newLine();
    }

    public void newLine() {
        write(System.lineSeparator());
    }

    public int getSize() {
        return size;
    }

    @Override
    public void close() {
        try {
            bw.close();
            fw.close();
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
