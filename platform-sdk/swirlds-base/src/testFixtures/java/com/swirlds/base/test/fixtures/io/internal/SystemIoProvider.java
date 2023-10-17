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

package com.swirlds.base.test.fixtures.io.internal;

import com.swirlds.base.test.fixtures.io.SystemErrProvider;
import com.swirlds.base.test.fixtures.io.SystemOutProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Implementation of {@link SystemOutProvider} and {@link SystemErrProvider}.
 */
public class SystemIoProvider implements SystemOutProvider, SystemErrProvider {

    private final ByteArrayOutputStream outputStream;

    private final StringBuilder internalBuilder = new StringBuilder();

    private boolean readAll = false;

    /**
     * Constructs a new instance.
     *
     * @param outputStream the output stream to read from
     */
    public SystemIoProvider(@NonNull final ByteArrayOutputStream outputStream) {
        this.outputStream = Objects.requireNonNull(outputStream, "outputStream must not be null");
    }

    /**
     * Reads all lines from the output stream and stores them in the internal builder.
     */
    private void readAll() {
        try {
            internalBuilder.setLength(0); // clear the builder
            internalBuilder.append(outputStream.toString());
        } catch (Exception e) {
            throw new RuntimeException("Error reading from outputStream", e);
        } finally {
            readAll = true;
        }
    }

    @Override
    public Stream<String> getLines() {
        if (!readAll) {
            readAll();
        }
        return internalBuilder.toString().lines();
    }
}
