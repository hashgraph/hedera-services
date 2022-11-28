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

import static com.hedera.services.ethereum.EthTxSigs.recoverAddressFromPubKey;
import static com.hedera.services.ledger.accounts.HederaAccountCustomizer.hasStakedId;
import static com.hedera.services.utils.EntityIdUtils.EVM_ADDRESS_SIZE;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.services.utils.MiscUtils.asPrimitiveKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.protobuf.ByteString;
import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InsufficientFundsException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.crypto.validators.CryptoCreateChecks;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
    static final int MAX_CHARGEABLE_AUTO_ASSOCIATIONS = 5000;
    private static final Logger log = LogManager.getLogger(CryptoCreateTransitionLogic.class);

    private final UsageLimits usageLimits;
    private final HederaLedger ledger;
    private final SigImpactHistorian sigImpactHistorian;
    private final TransactionContext txnCtx;
    private final GlobalDynamicProperties dynamicProperties;
    private final Supplier<AccountStorageAdapter> accounts;
    private final NodeInfo nodeInfo;
    private final AliasManager aliasManager;
    private final CryptoCreateChecks cryptoCreateChecks;

    @Inject
    public CryptoCreateTransitionLogic(
            final UsageLimits usageLimits,
            final HederaLedger ledger,
            final SigImpactHistorian sigImpactHistorian,
            final TransactionContext txnCtx,
            final GlobalDynamicProperties dynamicProperties,
            final Supplier<AccountStorageAdapter> accounts,
            final NodeInfo nodeInfo,
            final AliasManager aliasManager,
            final CryptoCreateChecks cryptoCreateChecks) {
        this.ledger = ledger;
        this.txnCtx = txnCtx;
        this.usageLimits = usageLimits;
        this.sigImpactHistorian = sigImpactHistorian;
        this.dynamicProperties = dynamicProperties;
        this.accounts = accounts;
        this.nodeInfo = nodeInfo;
        this.aliasManager = aliasManager;
        this.cryptoCreateChecks = cryptoCreateChecks;
    }

    @Override
    public void doStateTransition() {
        if (!usageLimits.areCreatableAccounts(1)) {
            txnCtx.setStatus(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
            return;
        }
        try {
            TransactionBody cryptoCreateTxn = txnCtx.accessor().getTxn();
            AccountID sponsor = txnCtx.activePayer();

            CryptoCreateTransactionBody op = cryptoCreateTxn.getCryptoCreateAccount();
            //            cryptoCreateTxn.getCryptoCreateAccount().getEvmAddress();
            long balance = op.getInitialBalance();
            final var customizer = asCustomizer(op);
            final var created = ledger.create(sponsor, balance, customizer);
            sigImpactHistorian.markEntityChanged(created.getAccountNum());

            txnCtx.setCreated(created);
            txnCtx.setStatus(SUCCESS);

            if (!op.getAlias().isEmpty()) {
                aliasManager.link(op.getAlias(), EntityNum.fromAccountId(created));
                if (op.getAlias().size() > EVM_ADDRESS_SIZE) {
                    final var key = asPrimitiveKeyUnchecked(op.getAlias());
                    final var jKey = asFcKeyUnchecked(key);
                    aliasManager.maybeLinkEvmAddress(jKey, EntityNum.fromAccountId(created));
                }
            } else {
                if (op.hasKey()
                        && !op.getKey().getECDSASecp256K1().isEmpty()
                        && dynamicProperties.isCryptoCreateWithAliasEnabled()) {
                    aliasManager.link(
                            (ByteString)
                                    ledger.getAccountsLedger().get(created, AccountProperty.ALIAS),
                            EntityNum.fromAccountId(created));
                }
            }
        } catch (InsufficientFundsException ife) {
            txnCtx.setStatus(INSUFFICIENT_PAYER_BALANCE);
        } catch (Exception e) {
            log.warn("Avoidable exception!", e);
            txnCtx.setStatus(FAIL_INVALID);
        }
    }

    private HederaAccountCustomizer asCustomizer(CryptoCreateTransactionBody op) {
        long autoRenewPeriod = op.getAutoRenewPeriod().getSeconds();
        long consensusTime = txnCtx.consensusTime().getEpochSecond();
        long expiry = consensusTime + autoRenewPeriod;

        var customizer = new HederaAccountCustomizer();
        customizer
                .memo(op.getMemo())
                .expiry(expiry)
                .autoRenewPeriod(autoRenewPeriod)
                .isReceiverSigRequired(op.getReceiverSigRequired())
                .maxAutomaticAssociations(op.getMaxAutomaticTokenAssociations())
                .isDeclinedReward(op.getDeclineReward());

        var emptyAlias = op.getAlias().isEmpty();
        if (!emptyAlias && !op.hasKey()) {
            if (op.getAlias().size() == EVM_ADDRESS_SIZE) {
                customizer.alias(op.getAlias());
            } else {
                final var keyFromAlias = asPrimitiveKeyUnchecked(op.getAlias());

                final JKey jKeyFromAlias = asFcKeyUnchecked(keyFromAlias);
                customizer.key(jKeyFromAlias).alias(op.getAlias());
            }
        } else if (!emptyAlias) {
            customizer.key(asFcKeyUnchecked(op.getKey())).alias(op.getAlias());
        } else {
            /* Note that {@code this.validate(TransactionBody)} will have rejected any txn with an invalid key. */
            if (!op.getKey().getECDSASecp256K1().isEmpty()
                    && dynamicProperties.isCryptoCreateWithAliasEnabled()) {

                final var recoveredEvmAddressFromPrimitiveKey =
                        recoverAddressFromPubKey(op.getKey().getECDSASecp256K1().toByteArray());

                if (recoveredEvmAddressFromPrimitiveKey != null) {
                    customizer.alias(ByteString.copyFrom(recoveredEvmAddressFromPrimitiveKey));
                }
            }

            final JKey key = asFcKeyUnchecked(op.getKey());
            customizer.key(key);
        }

        if (hasStakedId(op.getStakedIdCase().name())) {
            customizer.customizeStakedId(
                    op.getStakedIdCase().name(), op.getStakedAccountId(), op.getStakedNodeId());
        }
        return customizer;
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasCryptoCreateAccount;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return this::validate;
    }

    @SuppressWarnings("java:S1874")
    public ResponseCodeEnum validate(TransactionBody cryptoCreateTxn) {
        return cryptoCreateChecks.cryptoCreateValidation(
                cryptoCreateTxn.getCryptoCreateAccount(), accounts.get(), nodeInfo, aliasManager);
    }
}
