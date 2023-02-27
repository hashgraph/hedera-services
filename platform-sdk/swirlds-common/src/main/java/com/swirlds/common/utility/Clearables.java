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

package com.swirlds.common.utility;

import java.util.List;

/**
 * A utility class to hold a list of {@link Clearable} instances
 */
public final class Clearables implements Clearable {
    private final List<Clearable> list;

    private Clearables(final List<Clearable> list) {
        this.list = list;
    }

    @Override
    public void clear() {
        for (final Clearable clearable : list) {
            clearable.clear();
        }
    }

    /**
     * Combine a list of {@link Clearable} instances into a single one. The combined instance will execute clear on each
     * of the provided instances sequentially
     *
     * @param clearables
     * 		the instances to combine
     * @return a single {@link Clearable} that clears all instance provided
     */
    public static Clearables of(final Clearable... clearables) {
        return new Clearables(List.of(clearables));
    }
}
