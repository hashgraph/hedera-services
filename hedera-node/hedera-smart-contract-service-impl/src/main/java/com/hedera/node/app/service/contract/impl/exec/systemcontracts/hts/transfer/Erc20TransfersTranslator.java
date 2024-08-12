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

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Translates ERC-20 transfer calls to the HTS system contract.
 */
@Singleton
public class Erc20TransfersTranslator extends AbstractCallTranslator<HtsCallAttempt> {
    public static final Function ERC_20_TRANSFER = new Function("transfer(address,uint256)", ReturnTypes.BOOL);
    public static final Function ERC_20_TRANSFER_FROM =
            new Function("transferFrom(address,address,uint256)", ReturnTypes.BOOL);

    @Inject
    public Erc20TransfersTranslator() {
        // Dagger2
    }

    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        // We will match the transferFrom() selector shared by ERC-20 and ERC-721 if the token is missing
        return attempt.isTokenRedirect()
                && attempt.isSelector(ERC_20_TRANSFER, ERC_20_TRANSFER_FROM)
                && attempt.redirectTokenType() != NON_FUNGIBLE_UNIQUE;
    }

    @Override
    public @Nullable Call callFrom(@NonNull final HtsCallAttempt attempt) {
        if (attempt.isSelector(ERC_20_TRANSFER)) {
            final var call = Erc20TransfersTranslator.ERC_20_TRANSFER.decodeCall(
                    attempt.input().toArrayUnsafe());
            return callFrom(null, call.get(0), call.get(1), attempt, false);
        } else {
            final var call = Erc20TransfersTranslator.ERC_20_TRANSFER_FROM.decodeCall(
                    attempt.input().toArrayUnsafe());
            return callFrom(call.get(0), call.get(1), call.get(2), attempt, true);
        }
    }

    private Erc20TransfersCall callFrom(
            @Nullable final Address from,
            @NonNull final Address to,
            @NonNull final BigInteger amount,
            @NonNull final HtsCallAttempt attempt,
            final boolean requiresApproval) {
        return new Erc20TransfersCall(
                attempt.systemContractGasCalculator(),
                attempt.enhancement(),
                amount.longValueExact(),
                from,
                to,
                requireNonNull(attempt.redirectToken()).tokenIdOrThrow(),
                attempt.defaultVerificationStrategy(),
                attempt.senderId(),
                attempt.addressIdConverter(),
                requiresApproval,
                SPECIAL_REWARD_RECEIVERS);
    }
}
