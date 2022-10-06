/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.utils;

/**
 * For use when the overhead of an {@link java.util.concurrent.atomic.AtomicReference} is
 * unnecessary.
 *
 * @param <T> the type of value being referenced
 */
public class NonAtomicReference<T> {

    public NonAtomicReference() {}

    public NonAtomicReference(final T value) {
        this.value = value;
    }

    private T value;

    public T get() {
        return value;
    }

    public void set(final T value) {
        this.value = value;
    }
}
