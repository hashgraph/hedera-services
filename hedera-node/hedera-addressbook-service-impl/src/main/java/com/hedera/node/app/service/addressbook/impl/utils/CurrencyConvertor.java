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

package com.hedera.node.app.service.addressbook.impl.utils;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * This class contains all utility methods for converting currency.
 */
public class CurrencyConvertor {

    private CurrencyConvertor() {}

    public static long getTinybarsFromTinyCents(@NonNull final long tinyCents, @NonNull final ExchangeRate rate) {
        final var aMultiplier = BigInteger.valueOf(rate.hbarEquiv());
        final var bDivisor = BigInteger.valueOf(rate.centEquiv());
        return BigInteger.valueOf(tinyCents)
                .multiply(aMultiplier)
                .divide(bDivisor)
                .longValueExact();
    }

    public static long getFixedPriceInTinyCents(
            HederaFunctionality hederaFunctionality, SubType subType, AssetsLoader assetsLoader) {
        BigDecimal usdFee;
        try {
            usdFee = assetsLoader
                    .loadCanonicalPrices()
                    .get(fromPbj(hederaFunctionality))
                    .get(fromPbj(subType));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load canonical prices", e);
        }
        final var usdToTinyCents = BigDecimal.valueOf(100 * 100_000_000L);
        return usdToTinyCents.multiply(usdFee).longValue();
    }
}
