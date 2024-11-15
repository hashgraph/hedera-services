/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.hedera.embedded.fakes;

import com.hedera.node.app.tss.cryptography.pairings.api.FieldElement;
import com.hedera.node.app.tss.cryptography.pairings.api.Group;
import com.hedera.node.app.tss.cryptography.pairings.api.GroupElement;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.math.BigInteger;

public class FakeGroupElement implements GroupElement {
    public static final FakeGroupElement GENERATOR = new FakeGroupElement(BigInteger.valueOf(5L));
    private final BigInteger value;

    public FakeGroupElement(@NonNull BigInteger value) {
        this.value = value;
    }

    @Override
    public int size() {
        return value.bitLength();
    }

    @NonNull
    @Override
    public Group getGroup() {
        return null;
    }

    @NonNull
    @Override
    public GroupElement multiply(@NonNull final FieldElement other) {
        return new FakeGroupElement(value.multiply(new BigInteger(other.toBytes())));
    }

    @NonNull
    @Override
    public GroupElement add(@NonNull GroupElement other) {
        return new FakeGroupElement(value.add(new BigInteger(other.toBytes())));
    }

    @NonNull
    @Override
    public GroupElement copy() {
        return new FakeGroupElement(value);
    }

    @NonNull
    @Override
    public byte[] toBytes() {
        return value.toByteArray();
    }
}
