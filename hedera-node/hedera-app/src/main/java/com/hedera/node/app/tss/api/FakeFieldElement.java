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

package com.hedera.node.app.tss.api;

import com.hedera.node.app.tss.cryptography.pairings.api.Field;
import com.hedera.node.app.tss.cryptography.pairings.api.FieldElement;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;

public class FakeFieldElement implements FieldElement {

    private final BigInteger value;

    public FakeFieldElement(@NonNull BigInteger value) {
        this.value = value;
    }

    @NonNull
    @Override
    public Field field() {
        return null;
    }

    @NonNull
    @Override
    public FieldElement add(@NonNull final FieldElement other) {
        return new FakeFieldElement(value.add(new BigInteger(other.toBytes())));
    }

    @NonNull
    @Override
    public FieldElement subtract(@NonNull final FieldElement other) {
        return new FakeFieldElement(value.subtract(new BigInteger(other.toBytes())));
    }

    @NonNull
    @Override
    public FieldElement multiply(@NonNull final FieldElement other) {
        return new FakeFieldElement(value.multiply(new BigInteger(other.toBytes())));
    }

    @NonNull
    @Override
    public FieldElement power(final long exponent) {
        return new FakeFieldElement(value.pow((int) exponent));
    }

    @NonNull
    @Override
    public FieldElement inverse() {
        return new FakeFieldElement(value);
    }

    @NonNull
    @Override
    public BigInteger toBigInteger() {
        return value;
    }

    @NonNull
    @Override
    public byte[] toBytes() {
        return new byte[0];
    }
}
