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

package com.hedera.node.app.service.util.impl.test.handlers;

import com.hedera.node.app.service.util.impl.records.PrngRecordBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

public class FakePrngRecordBuilder implements PrngRecordBuilder {
    int entropyNumber;
    Bytes entropyBytes;

    @Override
    @NonNull
    public PrngRecordBuilder entropyNumber(final int num) {
        this.entropyNumber = num;
        return this;
    }

    @Override
    @NonNull
    public PrngRecordBuilder entropyBytes(@NonNull final Bytes prngBytes) {
        this.entropyBytes = prngBytes;
        return this;
    }
}
