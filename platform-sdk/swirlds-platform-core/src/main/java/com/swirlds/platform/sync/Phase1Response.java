/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.sync;

import com.swirlds.common.crypto.Hash;
import java.util.List;
import java.util.Objects;

public final class Phase1Response {
    private static final Phase1Response SYNC_REJECTED_RESPONSE = new Phase1Response(null, null);

    private final Generations generations;
    private final List<Hash> tips;

    private Phase1Response(final Generations generations, final List<Hash> tips) {
        this.generations = generations;
        this.tips = tips;
    }

    public static Phase1Response create(final Generations generations, final List<Hash> tips) {
        Objects.requireNonNull(generations, "generations cannot be null");
        Objects.requireNonNull(tips, "tips cannot be null");
        return new Phase1Response(generations, tips);
    }

    public static Phase1Response syncRejected() {
        return SYNC_REJECTED_RESPONSE;
    }

    public Generations getGenerations() {
        return generations;
    }

    public List<Hash> getTips() {
        return tips;
    }

    public boolean isSyncRejected() {
        return this == SYNC_REJECTED_RESPONSE;
    }
}
