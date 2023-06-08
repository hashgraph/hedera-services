/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hapi.fees.usage.util;

import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.INT_SIZE;

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.UtilPrngTransactionBody;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class UtilPrngMeta {
    private final long msgBytesUsed;

    public UtilPrngMeta(UtilPrngTransactionBody txn) {
        msgBytesUsed = txn.getRange() > 0 ? INT_SIZE : 0;
    }

    public UtilPrngMeta(UtilPrngMeta.Builder builder) {
        msgBytesUsed = builder.msgBytes;
    }

    public long getMsgBytesUsed() {
        return msgBytesUsed;
    }

    public static class Builder {
        private long msgBytes;

        public UtilPrngMeta.Builder msgBytesUsed(long msgBytes) {
            this.msgBytes = msgBytes;
            return this;
        }

        public Builder() {
            // empty here on purpose.
        }

        public UtilPrngMeta build() {
            return new UtilPrngMeta(this);
        }
    }

    public static UtilPrngMeta.Builder newBuilder() {
        return new UtilPrngMeta.Builder();
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
        return MoreObjects.toStringHelper(this)
                .add("msgBytesUsed", msgBytesUsed)
                .toString();
    }
}
