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

package com.hedera.services.bdd.spec.utilops.streams.assertions;

import com.hedera.services.bdd.spec.HapiSpec;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

/**
 * A validator for visible items, called once all items have been collected.
 */
public interface VisibleItemsValidator {
    /**
     * Asserts that the visible items are valid.
     *
     * @param spec the current spec
     * @param allItems all visible items
     * @throws AssertionError if the items are invalid
     */
    void assertValid(@NonNull HapiSpec spec, @NonNull Map<String, VisibleItems> allItems) throws AssertionError;
}
