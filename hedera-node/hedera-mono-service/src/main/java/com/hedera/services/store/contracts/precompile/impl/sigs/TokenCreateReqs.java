/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile.impl.sigs;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;

import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Encapsulates the logic to validate some signing requirements of a {@code TokenCreate} system
 * contract. Specifically, validates keys for:
 *
 * <ol>
 *   <li>The new token's auto-renew account (if present).
 *   <li>The new token's fractional fee collectors (if any).
 *   <li>The new token's self-denominated fixed fee collectors (if any).
 * </ol>
 *
 * Note the treasury and admin key signing requirements were already enforced in the {@link
 * com.hedera.services.store.contracts.precompile.impl.TokenCreatePrecompile} before this component
 * was introduced.
 */
public class TokenCreateReqs {
    private final MessageFrame frame;
    private final LegacyKeyValidator keyValidator;
    private final ContractAliases aliases;
    private final EvmSigsVerifier sigsVerifier;
    private final WorldLedgers ledgers;

    public interface Factory {
        TokenCreateReqs newReqs(
                MessageFrame frame,
                LegacyKeyValidator keyValidator,
                ContractAliases aliases,
                EvmSigsVerifier sigsVerifier,
                WorldLedgers ledgers);
    }

    public TokenCreateReqs(
            final MessageFrame frame,
            final LegacyKeyValidator keyValidator,
            final ContractAliases aliases,
            final EvmSigsVerifier sigsVerifier,
            final WorldLedgers ledgers) {
        this.frame = frame;
        this.aliases = aliases;
        this.ledgers = ledgers;
        this.sigsVerifier = sigsVerifier;
        this.keyValidator = keyValidator;
    }

    public void assertNonAdminOrTreasurySigs(final TokenCreateTransactionBody createOp) {
        if (createOp.hasAutoRenewAccount()) {
            validateLegacyAccountSig(
                    createOp.getAutoRenewAccount(), frame, INVALID_AUTORENEW_ACCOUNT);
        }
        for (var customFee : createOp.getCustomFeesList()) {
            final var collector = customFee.getFeeCollectorAccountId();
            // A fractional fee collector and a collector for a fixed fee denominated
            // in the units of the newly created token both must always sign a TokenCreate,
            // since these are automatically associated to the newly created token.
            boolean shouldAddCollector = false;
            if (customFee.hasFixedFee()) {
                final var fixedFee = customFee.getFixedFee();
                shouldAddCollector =
                        fixedFee.hasDenominatingTokenId()
                                && fixedFee.getDenominatingTokenId().getTokenNum() == 0L;
            } else if (customFee.hasFractionalFee()) {
                shouldAddCollector = true;
            } else {
                final var royaltyFee = customFee.getRoyaltyFee();
                if (royaltyFee.hasFallbackFee()) {
                    final var fFee = royaltyFee.getFallbackFee();
                    shouldAddCollector =
                            fFee.hasDenominatingTokenId()
                                    && fFee.getDenominatingTokenId().getTokenNum() == 0;
                }
            }
            if (shouldAddCollector) {
                validateLegacyAccountSig(collector, frame, INVALID_CUSTOM_FEE_COLLECTOR);
            }
        }
    }

    private void validateLegacyAccountSig(
            final AccountID id, final MessageFrame frame, final ResponseCodeEnum rcWhenMissing) {
        validateTrue(ledgers.accounts().exists(id), rcWhenMissing);
        final var hasSig =
                keyValidator.validateKey(
                        frame,
                        asTypedEvmAddress(id),
                        sigsVerifier::hasLegacyActiveKey,
                        ledgers,
                        aliases);
        validateTrue(hasSig, INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE);
    }
}
