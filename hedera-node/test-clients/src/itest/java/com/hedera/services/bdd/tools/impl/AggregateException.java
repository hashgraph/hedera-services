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

package com.hedera.services.bdd.tools.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/** An exception which aggegates a bunch of other exceptions
 *
 * Used for collecting a bunch of exceptions which happen "at the same level".  E.g., you can
 * process elements of a collection to the end, collecting the exceptions that occur along the way.
 */
public class AggregateException extends RuntimeException {
    public AggregateException() {
        super("aggregate");
    }

    public final List<Throwable> innerThrowables = new ArrayList<>(10);

    public AggregateException add(@NonNull final Throwable t) {
        innerThrowables.add(t);
        return this;
    }

    public boolean isEmpty() {
        return innerThrowables.isEmpty();
    }

    @NonNull
    @Override
    public String toString() {
        return "%s: %s".formatted(this.getClass().getSimpleName(), getMessage());
    }

    @NonNull
    @Override
    public String getMessage() {
        final var sb = new StringBuilder(1000);
        innerThrowables.forEach(t -> {
            sb.append(t);
            sb.append('\n');
        });
        return sb.toString();
    }
}
