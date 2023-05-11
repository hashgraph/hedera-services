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

package com.hedera.node.app.service.token.impl.records;

import com.hedera.node.app.spi.records.UniversalRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@code RecordBuilder} specialization for tracking the side effects of a {@code CryptoCreate}
 * transaction.
 */
public class CreateTokenRecordBuilder extends UniversalRecordBuilder<TokenCreateRecordBuilder>
        implements TokenCreateRecordBuilder {
    private long createdTokenNum = 0;

    /** {@inheritDoc} */
    @Override
    public TokenCreateRecordBuilder self() {
        return this;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public TokenCreateRecordBuilder setCreatedToken(final long num) {
        this.createdTokenNum = num;
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public long getCreatedToken() {
        throwIfMissingAccountNum();
        return createdTokenNum;
    }

    private void throwIfMissingAccountNum() {
        if (createdTokenNum == 0L) {
            throw new IllegalStateException("No new token number was recorded");
        }
    }
}
