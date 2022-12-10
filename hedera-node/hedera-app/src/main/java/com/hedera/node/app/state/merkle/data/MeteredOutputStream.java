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
package com.hedera.node.app.state.merkle.data;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class MeteredOutputStream extends FilterOutputStream {
    private int countWritten = 0;

    /**
     * Creates an output stream filter built on top of the specified underlying output stream.
     *
     * @param out the underlying output stream to be assigned to the field {@code this.out} for
     *     later use, or {@code null} if this instance is to be created without an underlying
     *     stream.
     */
    public MeteredOutputStream(OutputStream out) {
        super(out);
    }

    public int getCountWritten() {
        return countWritten;
    }

    @Override
    public void write(int b) throws IOException {
        super.write(b);
        countWritten++;
    }

    @Override
    public void write(byte[] b) throws IOException {
        super.write(b);
        countWritten += b.length;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        super.write(b, off, len);
        countWritten += len;
    }
}
