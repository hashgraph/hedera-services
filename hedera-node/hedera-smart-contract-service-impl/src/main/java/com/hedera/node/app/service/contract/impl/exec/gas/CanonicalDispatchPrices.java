/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.gas;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A component providing the canonical prices in tinycents for each type of transaction dispatch.
 */
@Singleton
public class CanonicalDispatchPrices {
    private final Map<DispatchType, Long> pricesMap = new EnumMap<>(DispatchType.class);
    public static final BigDecimal USD_TO_TINYCENTS = BigDecimal.valueOf(100 * 100_000_000L);

    @Inject
    public CanonicalDispatchPrices(@NonNull final AssetsLoader assetsLoader) {
        requireNonNull(assetsLoader);
        try {
            final var canonicalPrices = assetsLoader.loadCanonicalPrices().entrySet().stream()
                    .collect(toMap(
                            entry -> CommonPbjConverters.toPbj(entry.getKey()),
                            entry -> entry.getValue().entrySet().stream()
                                    .collect(toMap(
                                            subEntry -> CommonPbjConverters.toPbj(subEntry.getKey()),
                                            subEntry -> subEntry.getValue()
                                                    .multiply(USD_TO_TINYCENTS)
                                                    .longValue()))));
            Arrays.stream(DispatchType.class.getEnumConstants())
                    .map(dispatchType -> new AbstractMap.SimpleImmutableEntry<>(
                            dispatchType,
                            canonicalPrices
                                    .getOrDefault(dispatchType.functionality(), Collections.emptyMap())
                                    .get(dispatchType.subtype())))
                    .filter(entry -> entry.getValue() != null)
                    .forEach(entry -> pricesMap.put(entry.getKey(), entry.getValue()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the canonical price of a dispatch in tinycents, according to the assets bundled
     * in the deployed jar.
     *
     * @param dispatchType the type of dispatch
     * @return the canonical price of the dispatch in tinycents
     */
    public long canonicalPriceInTinycents(@NonNull final DispatchType dispatchType) {
        return Objects.requireNonNull(pricesMap.get(dispatchType));
    }
}
