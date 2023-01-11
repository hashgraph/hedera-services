/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.hapi.fees.usage.token.meta;

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.SubType;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class TokenBurnWipeMeta extends TokenOpMetaBase {
    private final int serialNumsCount;

    public TokenBurnWipeMeta(
            final int bpt,
            final SubType subType,
            final long transferRecordRb,
            final int serialNumsCount) {
        super(bpt, subType, transferRecordRb);
        this.serialNumsCount = serialNumsCount;
    }

    public int getSerialNumsCount() {
        return serialNumsCount;
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
    public MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper().add("serialNumsCount", serialNumsCount);
    }
}
