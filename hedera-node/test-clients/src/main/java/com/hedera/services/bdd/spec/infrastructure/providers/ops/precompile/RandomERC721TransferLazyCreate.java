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

package com.hedera.services.bdd.spec.infrastructure.providers.ops.precompile;

import static com.hedera.node.app.hapi.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.regression.factories.IdFuzzingProviderFactory.*;
import static com.hedera.services.bdd.suites.utils.ECDSAKeysUtils.getEvmAddressFromString;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractCall;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import java.math.BigInteger;
import java.util.Optional;

public class RandomERC721TransferLazyCreate implements OpProvider {
    private final HapiSpecRegistry registry;
    private static final long GAS_TO_OFFER = 5_000_000L;
    private static final String TRANSFER_FROM_ACCOUNT_TXN = "transferFromAccountTxn";
    private static final String TRANSFER_FROM = "transferFrom";
    private final EntityNameProvider keys;

    public RandomERC721TransferLazyCreate(HapiSpecRegistry registry, EntityNameProvider keys) {
        this.registry = registry;
        this.keys = keys;
    }

    private Optional<String> randomKey() {
        return keys.getQualifying().filter(k -> !k.isEmpty());
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        return randomKey().map(this::generateERC721TransferLazyCreate);
    }

    private HapiContractCall generateERC721TransferLazyCreate(String evmAddressRecipient) {
        final var addressBytes = recoverAddressFromPubKey(getEvmAddressFromString(registry, evmAddressRecipient));
        /**
         * CONTRACT_REVERT_EXECUTED is also included in the outcomes, because we have a predefined amount of
         * ERC721 tokens and predefined allowances. As more and more transfers happen in the fuzzing test once the
         * predefined allowance limit is reached transfers are impossible and the actual error in the precompile is
         * SPENDER_DOES_NOT_HAVE_ALLOWANCE
         */
        return contractCall(
                        ERC_721_CONTRACT,
                        TRANSFER_FROM,
                        HapiParserUtil.asHeadlongAddress(asAddress(registry.getTokenID(ERC_NON_FUNGIBLE_TOKEN))),
                        HapiParserUtil.asHeadlongAddress(asAddress(registry.getAccountID(OWNER))),
                        HapiParserUtil.asHeadlongAddress(addressBytes),
                        BigInteger.valueOf(1))
                .via(TRANSFER_FROM_ACCOUNT_TXN)
                .gas(GAS_TO_OFFER)
                .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
                .hasKnownStatusFrom(SUCCESS, CONTRACT_REVERT_EXECUTED);
    }
}
