/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.constructable.internal;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

/**
 * Used as a wrapper for a {@link Method} to compare if the methods have the same signature
 */
public record MethodWrapper(Method method) {
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MethodWrapper that)) {
            return false;
        }
        return Objects.equals(method.getName(), that.method.getName())
                && Arrays.equals(method.getParameterTypes(), that.method.getParameterTypes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(method.getName(), Arrays.hashCode(method.getParameterTypes()));
    }
}
