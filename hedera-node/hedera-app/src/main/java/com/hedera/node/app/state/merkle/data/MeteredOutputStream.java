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

package com.hedera.node.app.state.merkle.data;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/** Simple {@link OutputStream} that wraps another and counts all bytes written. */
/*@NotThreadSafe*/
public final class MeteredOutputStream extends FilterOutputStream {
    private int countWritten = 0;

    /**
     * Creates an output stream filter built on top of the specified underlying output stream.
     *
     * @param out the underlying output stream to delegate writes to.
     */
    public MeteredOutputStream(@NonNull OutputStream out) {
        super(Objects.requireNonNull(out));
    }

    /** Gets the number of bytes written */
    public int getCountWritten() {
        return countWritten;
    }

    /** {@inheritDoc} */
    @Override
    public void write(int b) throws IOException {
        super.write(b);
        countWritten++;
    }

    /** {@inheritDoc} */
    @Override
    public void write(@NonNull byte[] b) throws IOException {
        super.write(b);
        countWritten += b.length;
    }

    /** {@inheritDoc} */
    @Override
    public void write(@NonNull byte[] b, int off, int len) throws IOException {
        super.write(b, off, len);
        countWritten += len;
    }
}
