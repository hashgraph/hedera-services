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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer;

import static com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.SpecialRewardReceivers.SPECIAL_REWARD_RECEIVERS;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Translates ERC-721 {@code transferFrom()} calls to the HTS system contract.
 */
@Singleton
public class Erc721TransferFromTranslator extends AbstractCallTranslator<HtsCallAttempt> {

    /**
     * Selector for transferFrom(address,address,uint256) method.
     */
    public static final Function ERC_721_TRANSFER_FROM = new Function("transferFrom(address,address,uint256)");

    /**
     * Default constructor for injection.
     */
    @Inject
    public Erc721TransferFromTranslator() {
        // Dagger2
    }

    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        // We only match calls to existing tokens (i.e., with known token type)
        return attempt.isTokenRedirect()
                && attempt.isSelector(ERC_721_TRANSFER_FROM)
                && attempt.redirectTokenType() == NON_FUNGIBLE_UNIQUE;
    }

    @Override
    public Call callFrom(@NonNull final HtsCallAttempt attempt) {
        final var call = Erc721TransferFromTranslator.ERC_721_TRANSFER_FROM.decodeCall(
                attempt.input().toArrayUnsafe());
        return new Erc721TransferFromCall(
                ((BigInteger) call.get(2)).longValueExact(),
                call.get(0),
                call.get(1),
                requireNonNull(attempt.redirectToken()).tokenIdOrThrow(),
                attempt.defaultVerificationStrategy(),
                attempt.enhancement(),
                attempt.systemContractGasCalculator(),
                attempt.senderId(),
                attempt.addressIdConverter(),
                SPECIAL_REWARD_RECEIVERS);
    }
}
