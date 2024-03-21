/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.crypto;

import com.swirlds.common.constructable.ConstructableIgnored;
import java.util.Arrays;

@ConstructableIgnored
public class ImmutableHash extends Hash {

    public ImmutableHash() {}

    public ImmutableHash(final byte[] value) {
        super(value, DigestType.SHA_384, true, true);
    }

    public ImmutableHash(final byte[] value, final DigestType digestType) {
        super(value, digestType, true, true);
    }

    public ImmutableHash(final Hash mutable) {
        super(mutable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] getValue() {
        final byte[] value = super.getValue();
        return Arrays.copyOf(value, value.length);
    }
}
