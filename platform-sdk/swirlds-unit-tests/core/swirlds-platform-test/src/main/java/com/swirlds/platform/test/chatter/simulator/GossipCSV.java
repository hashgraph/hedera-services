/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.chatter.simulator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Utility class for writing data about a gossip simulation to a CSV.
 */
public class GossipCSV {

    private final File file;
    private final BufferedWriter out;

    public GossipCSV(final File file, final List<String> headers) {
        this.file = file;
        try {
            out = new BufferedWriter(new FileWriter(file));
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }

        writeLine(headers);
    }

    /**
     * Write a single line to the CSV.
     *
     * @param line
     * 		elements to write to the next line
     */
    public void writeLine(final List<String> line) {
        final StringBuilder sb = new StringBuilder();
        for (int index = 0; index < line.size(); index++) {
            sb.append(line.get(index));
            if (index + 1 < line.size()) {
                sb.append(",");
            }
        }
        sb.append("\n");
        try {
            out.write(sb.toString());
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Close the file.
     */
    public void close() {
        try {
            out.close();
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
