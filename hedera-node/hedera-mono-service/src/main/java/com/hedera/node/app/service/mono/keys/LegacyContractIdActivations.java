/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.keys;

import static com.hedera.node.app.hapi.utils.sysfiles.ParsingUtils.fromTwoPartDelimited;
import static com.hedera.node.app.service.mono.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.lang3.tuple.Pair;
import org.hyperledger.besu.datatypes.Address;

/**
 * A usable representation of the {@code contracts.keys.legacyActivations} property.
 *
 * @param privilegedContracts
 */
public record LegacyContractIdActivations(Map<Address, Set<Address>> privilegedContracts) {
    private static Pattern CONTRACTS_PATTERN = Pattern.compile("\\[(.*)\\]");

    /**
     * Given a spec of the form {@code 0.0.Xby[0.0.A|0.0.B|...],0.0.Yby[0.0.C],...}, returns the
     * {@link LegacyContractIdActivations} that represents these legacy activations.
     *
     * @param spec the property value
     * @return the representative instance
     */
    public static LegacyContractIdActivations from(final String spec) {
        final var privilegedContracts =
                Arrays.stream(spec.split(","))
                        .filter(s -> !s.isBlank())
                        .map(
                                literal ->
                                        fromTwoPartDelimited(
                                                literal,
                                                "by",
                                                (account, contracts) -> {},
                                                LegacyContractIdActivations::parsedMirrorAddressOf,
                                                LegacyContractIdActivations::contracts,
                                                Pair::of))
                        .collect(toMap(Pair::getKey, Pair::getValue));
        return new LegacyContractIdActivations(privilegedContracts);
    }

    /**
     * Returns all the unique contract addresses that have legacy activation privileges for a given
     * account.
     *
     * @param account an account whose key is being validated
     * @return any legacy contract activations, or null if none exist
     */
    @Nullable
    public Set<Address> getLegacyActiveContractsFor(final Address account) {
        return privilegedContracts.get(account);
    }

    private static Set<Address> contracts(final String bracketed) {
        final var m = CONTRACTS_PATTERN.matcher(bracketed);
        if (!m.matches()) {
            throw new IllegalArgumentException("'" + bracketed + "' is not a contract id list");
        }
        final var psv = m.group(1);
        return Arrays.stream(psv.split("[|]"))
                .mapToLong(Long::parseLong)
                .mapToObj(LegacyContractIdActivations::mirrorAddressOf)
                .collect(toSet());
    }

    private static Address parsedMirrorAddressOf(final String s) {
        return mirrorAddressOf(Long.parseLong(s));
    }

    private static Address mirrorAddressOf(final long l) {
        return EntityIdUtils.asTypedEvmAddress(STATIC_PROPERTIES.scopedEntityIdWith(l));
    }
}
