/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.validation;

import static com.hedera.services.context.properties.PropertyNames.LEDGER_TOTAL_TINY_BAR_FLOAT;

import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.swirlds.merkle.map.MerkleMap;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class BasedLedgerValidator implements LedgerValidator {
    private final long expectedFloat;

    @Inject
    public BasedLedgerValidator(final @CompositeProps PropertySource properties) {
        this.expectedFloat = properties.getLongProperty(LEDGER_TOTAL_TINY_BAR_FLOAT);
    }

    @Override
    public void validate(MerkleMap<EntityNum, MerkleAccount> accounts) {
        var totalFloat = new AtomicReference<>(BigInteger.ZERO);
        MiscUtils.forEach(
                accounts,
                (id, account) -> {
                    final var num = id.longValue();
                    if (num < 1) {
                        throw new IllegalStateException(
                                String.format("Invalid num in account %s", id.toIdString()));
                    }
                    totalFloat.set(totalFloat.get().add(BigInteger.valueOf(account.getBalance())));
                });
        try {
            final var actualFloat = totalFloat.get().longValueExact();
            if (actualFloat != expectedFloat) {
                throw new IllegalStateException(
                        "Wrong ℏ float, expected " + expectedFloat + " but was " + actualFloat);
            }
        } catch (ArithmeticException ae) {
            throw new IllegalStateException(
                    "Wrong ℏ float, expected " + expectedFloat + " but overflowed instead");
        }
    }
}
