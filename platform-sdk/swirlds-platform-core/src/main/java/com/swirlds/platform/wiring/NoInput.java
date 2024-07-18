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

package com.swirlds.platform.wiring;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A singleton class that is used to invoke methods on schedulers that do not require any input. Since the current
 * framework does not support such methods, this class is used as a placeholder. This will be removed once the
 * framework is updated to support such methods.
 */
public final class NoInput {
    private static final NoInput INSTANCE = new NoInput();

    private NoInput() {}

    /**
     * @return the singleton instance of this class
     */
    @NonNull
    public static NoInput getInstance() {
        return INSTANCE;
    }
}
