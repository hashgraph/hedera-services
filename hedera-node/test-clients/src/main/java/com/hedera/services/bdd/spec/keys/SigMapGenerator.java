/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.keys;

import static java.util.Map.Entry;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hederahashgraph.api.proto.java.SignatureMap;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * A generator of {@link SignatureMap} instances whose public key prefixes have a given
 * nature. Generally that nature is that each prefix is be the shortest unique prefix to
 * identify its corresponding public key among all that sign; though in some cases we
 * must always include the full prefix. (E.g., to trigger a hollow account completion.)
 *
 * <p>For negative testing there are also natures like {@link Nature#AMBIGUOUS_PREFIXES}
 * and {@link Nature#CONFUSED_PREFIXES} which may construct invalid maps.
 */
public interface SigMapGenerator {
    enum Nature {
        UNIQUE_PREFIXES,
        AMBIGUOUS_PREFIXES,
        CONFUSED_PREFIXES,
        UNIQUE_WITH_SOME_FULL_PREFIXES,
    }

    SignatureMap forPrimitiveSigs(@Nullable HapiSpec spec, List<Entry<byte[], byte[]>> keySigs);
}
