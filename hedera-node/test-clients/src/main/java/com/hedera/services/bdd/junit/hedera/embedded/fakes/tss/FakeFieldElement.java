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

package com.hedera.services.bdd.junit.hedera.embedded.fakes.tss;

import com.hedera.cryptography.pairings.api.Field;
import com.hedera.cryptography.pairings.api.FieldElement;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import org.jetbrains.annotations.NotNull;

public class FakeFieldElement implements FieldElement {

    private final BigInteger value;

    public FakeFieldElement(@NonNull BigInteger value) {
        this.value = value;
    }

    @NotNull
    @Override
    public Field getField() {
        return null;
    }

    @NotNull
    @Override
    public FieldElement add(@NotNull FieldElement fieldElement) {
        return new FakeFieldElement(value.add(fieldElement.toBigInteger()));
    }

    @NotNull
    @Override
    public FieldElement subtract(@NotNull FieldElement fieldElement) {
        return new FakeFieldElement(value.subtract(fieldElement.toBigInteger()));
    }

    @NotNull
    @Override
    public FieldElement multiply(@NotNull FieldElement fieldElement) {
        return new FakeFieldElement(value.multiply(fieldElement.toBigInteger()));
    }

    @NotNull
    @Override
    public FieldElement power(long l) {
        return new FakeFieldElement(value.pow((int) l));
    }

    @NotNull
    @Override
    public FieldElement inverse() {
        return new FakeFieldElement(value.modInverse(BigInteger.TEN.pow(1_000_000)));
    }

    @NotNull
    @Override
    public BigInteger toBigInteger() {
        return value;
    }

    @NotNull
    @Override
    public byte[] toBytes() {
        return value.toByteArray();
    }
}
