/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.usage.util;

import static com.hederahashgraph.fee.FeeBuilder.INT_SIZE;

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.PrngTransactionBody;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class PrngMeta {
    private final long msgBytesUsed;

    public PrngMeta(PrngTransactionBody txn) {
        msgBytesUsed = txn.getRange() > 0 ? INT_SIZE : 0;
    }

    public PrngMeta(PrngMeta.Builder builder) {
        msgBytesUsed = builder.msgBytes;
    }

    public long getMsgBytesUsed() {
        return msgBytesUsed;
    }

    public static class Builder {
        private long msgBytes;

        public PrngMeta.Builder msgBytesUsed(long msgBytes) {
            this.msgBytes = msgBytes;
            return this;
        }

        public Builder() {
            // empty here on purpose.
        }

        public PrngMeta build() {
            return new PrngMeta(this);
        }
    }

    public static PrngMeta.Builder newBuilder() {
        return new PrngMeta.Builder();
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("msgBytesUsed", msgBytesUsed).toString();
    }
}
