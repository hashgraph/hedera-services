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

package com.swirlds.common.test.io.extendable;

import static com.swirlds.common.io.extendable.ExtendableInputStream.extendInputStream;
import static com.swirlds.common.io.extendable.ExtendableOutputStream.extendOutputStream;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.io.extendable.extensions.CountingStreamExtension;
import com.swirlds.common.io.extendable.extensions.ExpiringStreamExtension;
import com.swirlds.common.io.extendable.extensions.HashingStreamExtension;
import com.swirlds.common.io.extendable.extensions.MaxSizeStreamExtension;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Multi-Extension Tests")
class MultiExtensionTests {

    @Test
    @DisplayName("Input Stream Sanity Test")
    void inputStreamSanityTest() throws IOException {
        StreamSanityChecks.inputStreamSanityCheck((final InputStream base) -> extendInputStream(
                base,
                new CountingStreamExtension(),
                new ExpiringStreamExtension(Duration.ofHours(1)),
                new HashingStreamExtension(DigestType.SHA_384),
                new MaxSizeStreamExtension(Long.MAX_VALUE) /*,
						new ThrottleStreamExtension(Long.MAX_VALUE),
						new TimeoutStreamExtension(Duration.ofHours(1))TODO*/));
    }

    @Test
    @DisplayName("Output Stream Sanity Test")
    void outputStreamSanityTest() throws IOException {
        StreamSanityChecks.outputStreamSanityCheck((final OutputStream base) -> extendOutputStream(
                base,
                new CountingStreamExtension(),
                new ExpiringStreamExtension(Duration.ofHours(1)),
                new HashingStreamExtension(DigestType.SHA_384),
                new MaxSizeStreamExtension(Long.MAX_VALUE) /*,
						new ThrottleStreamExtension(Long.MAX_VALUE),
						new TimeoutStreamExtension(Duration.ofHours(1))TODO*/));
    }
}
