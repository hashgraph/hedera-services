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

package com.hedera.node.app.service.mono.state.codec;

import com.hedera.pbj.runtime.io.DataInput;
import com.swirlds.common.merkle.MerkleLeaf;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Temporary adapter to let us re-use {@link MerkleLeaf}, {@link com.swirlds.virtualmap.VirtualKey},
 * and {@link com.swirlds.virtualmap.VirtualValue} serialization and deserialization logic for
 * {@link com.hedera.pbj.runtime.Codec} implementations.
 */
public class PbjInputStream extends InputStream {
    private final DataInput in;

    private PbjInputStream(@NonNull final DataInput in) {
        this.in = Objects.requireNonNull(in);
    }

    public static @NonNull PbjInputStream wrapping(@NonNull final DataInput in) {
        return new PbjInputStream(Objects.requireNonNull(in));
    }

    @Override
    public int read() throws IOException {
        return in.readByte();
    }
}
