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

package com.swirlds.common.wiring.transformers;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;

// TODO test

/**
 * Transforms a collection of items to a sequence of individual items. Expects that there will not be any null values in
 * the collection.
 */
public class WireCollectionSplitter<T> extends AbstractWireTransformer<Collection<T>, T, WireCollectionSplitter<T>> {

    /**
     * {@inheritDoc}
     */
    @Override
    public void accept(@NonNull final Collection<T> collection) {
        for (final T t : collection) {
            forward(t);
        }
    }
}
