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

package com.swirlds.platform.config;

import com.swirlds.common.config.sources.AliasConfigSource;
import com.swirlds.config.api.source.ConfigSource;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Adds aliases for config parameters that have changed their name so that the old name can still be supported
 */
public final class ConfigAliases {
    private ConfigAliases() {}

    private static final List<Pair<String, String>> ALIASES = List.of(
            Pair.of("consensus.roundsNonAncient", "state.roundsNonAncient"),
            Pair.of("consensus.roundsExpired", "state.roundsExpired"),
            Pair.of("consensus.coinFreq", "coinFreq"));

    /**
     * Add all known aliases to the provided config source
     *
     * @param configSource
     * 		the source to add aliases to
     * @return the original source with added aliases
     */
    public static ConfigSource addConfigAliases(final ConfigSource configSource) {
        final AliasConfigSource withAliases = new AliasConfigSource(configSource);
        for (final Pair<String, String> alias : ALIASES) {
            withAliases.addAlias(alias.getLeft(), alias.getRight());
        }

        return withAliases;
    }
}
