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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import static com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations.MISSING_ENTITY_NUMBER;
import static com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations.NON_CANONICAL_REFERENCE_NUMBER;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.accountNumberForEvmReference;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.explicitFromHeadlong;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.isLongZeroAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.numberOfLongZero;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Helper class for determining the synthetic id to use in a synthetic
 * {@link com.hedera.hapi.node.token.CryptoTransferTransactionBody}.
 */
@Singleton
public class SyntheticIds {
    private static final AccountID DEBIT_NON_CANONICAL_REFERENCE_ID =
            AccountID.newBuilder().accountNum(0L).build();
    private static final AccountID CREDIT_NON_CANONICAL_REFERENCE_ID =
            AccountID.newBuilder().alias(Bytes.wrap(new byte[20])).build();

    @Inject
    public SyntheticIds() {
        // Dagger2
    }

    /**
     * Given a native operations, returns the {@link AddressIdConverter} to use for converting
     * addresses to ids in this context.
     *
     * @param nativeOperations the native operations
     * @return the converter
     */
    public AddressIdConverter converterFor(@NonNull final HederaNativeOperations nativeOperations) {
        return new AddressIdConverter() {
            @Override
            public @NonNull AccountID convert(@NonNull final Address address) {
                return syntheticIdFor(address, nativeOperations);
            }

            @Override
            public @NonNull AccountID convertCredit(@NonNull Address address) {
                return syntheticIdForCredit(address, nativeOperations);
            }
        };
    }

    private static @NonNull AccountID syntheticIdFor(
            @NonNull final Address address, @NonNull final HederaNativeOperations nativeOperations) {
        return internalSyntheticId(false, address, nativeOperations);
    }

    private static @NonNull AccountID syntheticIdForCredit(
            @NonNull final Address address, @NonNull final HederaNativeOperations nativeOperations) {
        return internalSyntheticId(true, address, nativeOperations);
    }

    private static @NonNull AccountID internalSyntheticId(
            final boolean isCredit,
            @NonNull final Address address,
            @NonNull final HederaNativeOperations nativeOperations) {
        requireNonNull(address);
        final var accountNum = accountNumberForEvmReference(address, nativeOperations);
        if (accountNum == MISSING_ENTITY_NUMBER) {
            final var explicit = explicitFromHeadlong(address);
            if (isLongZeroAddress(explicit)) {
                // References to missing long-zero addresses are synthesized as aliases for
                // credits and numeric ids for debits
                return isCredit ? aliasIdWith(explicit) : numericIdWith(numberOfLongZero(explicit));
            } else {
                // References to missing EVM addresses are always synthesized as alias ids
                return aliasIdWith(explicit);
            }
        } else if (accountNum == NON_CANONICAL_REFERENCE_NUMBER) {
            // Non-canonical references result are synthesized as ids of the zero address,
            // using a numeric id for a debit and an alias id for a credit
            return isCredit ? CREDIT_NON_CANONICAL_REFERENCE_ID : DEBIT_NON_CANONICAL_REFERENCE_ID;
        } else {
            // Canonical references are translated to numeric ids
            return numericIdWith(accountNum);
        }
    }

    private static AccountID numericIdWith(final long number) {
        return AccountID.newBuilder().accountNum(number).build();
    }

    private static AccountID aliasIdWith(final byte[] alias) {
        return AccountID.newBuilder().alias(Bytes.wrap(alias)).build();
    }
}
