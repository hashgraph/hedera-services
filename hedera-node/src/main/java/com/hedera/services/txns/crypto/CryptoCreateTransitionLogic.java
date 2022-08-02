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
package com.hedera.services.txns.crypto;

import static com.hedera.services.ledger.accounts.HederaAccountCustomizer.hasStakedId;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.exceptions.InsufficientFundsException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.utils.accessors.custom.CryptoCreateAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implements the {@link TransitionLogic} for a HAPI CryptoCreate transaction, and the conditions
 * under which such logic is syntactically correct. (It is possible that the <i>semantics</i> of the
 * transaction will still be wrong; for example, if the sponsor account can no longer afford to fund
 * the initial balance of the new account.)
 */
@Singleton
public class CryptoCreateTransitionLogic implements TransitionLogic {
    private static final Logger log = LogManager.getLogger(CryptoCreateTransitionLogic.class);
    private final UsageLimits usageLimits;
    private final HederaLedger ledger;
    private final SigImpactHistorian sigImpactHistorian;
    private final TransactionContext txnCtx;

    @Inject
    public CryptoCreateTransitionLogic(
            final UsageLimits usageLimits,
            final HederaLedger ledger,
            final SigImpactHistorian sigImpactHistorian,
            final TransactionContext txnCtx) {
        this.ledger = ledger;
        this.txnCtx = txnCtx;
        this.usageLimits = usageLimits;
        this.sigImpactHistorian = sigImpactHistorian;
    }

    @Override
    public void doStateTransition() {
        if (!usageLimits.areCreatableAccounts(1)) {
            txnCtx.setStatus(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
            return;
        }
        try {
            final var accessor = (CryptoCreateAccessor) txnCtx.swirldsTxnAccessor().getDelegate();
            AccountID sponsor = txnCtx.activePayer();

            long balance = accessor.initialBalance();
            final var customizer = asCustomizer(accessor);
            final var created = ledger.create(sponsor, balance, customizer);
            sigImpactHistorian.markEntityChanged(created.getAccountNum());

            txnCtx.setCreated(created);
            txnCtx.setStatus(SUCCESS);
        } catch (InsufficientFundsException ife) {
            txnCtx.setStatus(INSUFFICIENT_PAYER_BALANCE);
        } catch (Exception e) {
            log.warn("Avoidable exception!", e);
            txnCtx.setStatus(FAIL_INVALID);
        }
    }

    private HederaAccountCustomizer asCustomizer(final CryptoCreateAccessor accessor) {
        final long autoRenewPeriod = accessor.autoRenewPeriod().getSeconds();
        final long consensusTime = txnCtx.consensusTime().getEpochSecond();
        final long expiry = consensusTime + autoRenewPeriod;

        final var memo = accessor.memo();
        final var receiverSigReq = accessor.receiverSigReq();
        final var maxTokenAssoc = accessor.maxTokenAssociations();
        final var declineReward = accessor.declineReward();
        final var stakedIdCase = accessor.stakedIdCase();
        final var stakedAccountId = accessor.stakedAccountId();
        final var stakedNodeId = accessor.stakedNodeId();

        /* Note that {@code accessor.validateSyntax()} will have rejected any txn with an invalid key. */
        final JKey key = asFcKeyUnchecked(accessor.key());
        HederaAccountCustomizer customizer =
                new HederaAccountCustomizer()
                        .key(key)
                        .memo(memo)
                        .expiry(expiry)
                        .autoRenewPeriod(autoRenewPeriod)
                        .isReceiverSigRequired(receiverSigReq)
                        .maxAutomaticAssociations(maxTokenAssoc)
                        .isDeclinedReward(declineReward);

        if (hasStakedId(stakedIdCase.name())) {
            customizer.customizeStakedId(stakedIdCase.name(), stakedAccountId, stakedNodeId);
        }
        return customizer;
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasCryptoCreateAccount;
    }
}
