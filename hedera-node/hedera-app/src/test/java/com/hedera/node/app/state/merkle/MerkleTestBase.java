/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.state.merkle;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.merkle.MerkleNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;

class MerkleTestBase {
    @BeforeEach
    protected void setUp() {
        // Unfortunately, we need to configure the ConstructableRegistry for serialization tests and
        // even for basic usage of the MerkleMap (it uses it internally to make copies of internal
        // nodes).
        try {
            final ConstructableRegistry registry = ConstructableRegistry.getInstance();
            registry.reset();
            registry.registerConstructables("com.swirlds.merklemap");
            registry.registerConstructables("com.swirlds.jaspermap");
            registry.registerConstructables("com.swirlds.common.merkle");
            registry.registerConstructables("com.swirlds.merkle");
            registry.registerConstructables("com.swirlds.merkle.tree");
            registry.registerConstructables("com.hedera.node.app.state.merkle");
        } catch (ConstructableRegistryException ex) {
            throw new AssertionError(ex);
        }
    }

    protected byte[] writeTree(@NonNull final MerkleNode tree, @NonNull final Path tempDir)
            throws IOException {
        final var byteOutputStream = new ByteArrayOutputStream();
        try (final var out = new MerkleDataOutputStream(byteOutputStream)) {
            out.writeMerkleTree(tempDir, tree);
        }
        return byteOutputStream.toByteArray();
    }

    protected <T extends MerkleNode> T parseTree(
            @NonNull final byte[] state, @NonNull final Path tempDir) throws IOException {
        final var byteInputStream = new ByteArrayInputStream(state);
        try (final var in = new MerkleDataInputStream(byteInputStream)) {
            return in.readMerkleTree(tempDir, 100);
        }
    }

    static void writeString(String value, OutputStream out) throws IOException {
        if (value == null) {
            out.write(0);
        } else {
            final var bytes = value.getBytes(StandardCharsets.UTF_8);
            out.write(bytes.length);
            out.write(bytes);
        }
    }

    static String parseString(InputStream in, int version) throws IOException {
        final var len = in.read();
        return len == 0 ? null : new String(in.readNBytes(len), StandardCharsets.UTF_8);
    }

    static void writeInteger(int value, OutputStream out) throws IOException {
        try (final var dout = new DataOutputStream(out)) {
            dout.writeInt(value);
        }
    }

    static int parseInteger(InputStream in, int version) throws IOException {
        try (final var din = new DataInputStream(in)) {
            return din.readInt();
        }
    }
}
