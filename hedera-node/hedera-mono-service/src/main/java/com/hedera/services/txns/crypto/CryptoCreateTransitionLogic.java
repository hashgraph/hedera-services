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
import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.services.utils.MiscUtils.asPrimitiveKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_INITIAL_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RECEIVE_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SEND_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STAKING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_REQUIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.STAKING_NOT_ENABLED;
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
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Arrays;
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
    private final OptionValidator validator;
    private final SigImpactHistorian sigImpactHistorian;
    private final TransactionContext txnCtx;
    private final GlobalDynamicProperties dynamicProperties;
    private final Supplier<AccountStorageAdapter> accounts;
    private final NodeInfo nodeInfo;
    private final AliasManager aliasManager;

    @Inject
    public CryptoCreateTransitionLogic(
            final UsageLimits usageLimits,
            final HederaLedger ledger,
            final OptionValidator validator,
            final SigImpactHistorian sigImpactHistorian,
            final TransactionContext txnCtx,
            final GlobalDynamicProperties dynamicProperties,
            final Supplier<AccountStorageAdapter> accounts,
            final NodeInfo nodeInfo,
            final AliasManager aliasManager) {
        this.ledger = ledger;
        this.txnCtx = txnCtx;
        this.usageLimits = usageLimits;
        this.validator = validator;
        this.sigImpactHistorian = sigImpactHistorian;
        this.dynamicProperties = dynamicProperties;
        this.accounts = accounts;
        this.nodeInfo = nodeInfo;
        this.aliasManager = aliasManager;
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

    private ResponseCodeEnum isUsedAsAliasCheck(ByteString alias) {
        if (!aliasManager.lookupIdBy(alias).equals(MISSING_NUM)) {
            return INVALID_ALIAS_KEY;
        }
        return OK;
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
        CryptoCreateTransactionBody op = cryptoCreateTxn.getCryptoCreateAccount();

        var memoValidity = validator.memoCheck(op.getMemo());
        if (memoValidity != OK) {
            return memoValidity;
        }

        if (!op.getAlias().isEmpty() && op.getAlias().size() < EVM_ADDRESS_SIZE) {
            return INVALID_ALIAS_KEY;
        }

        var noAliasValidity = validateNoAliasCase(op);
        if (noAliasValidity != OK) {
            return noAliasValidity;
        }

        var aliasBiggerThanEVMAddressSizeValidity = validateAliasBiggerThanEVMAddressSizeCase(op);
        if (aliasBiggerThanEVMAddressSizeValidity != OK) {
            return aliasBiggerThanEVMAddressSizeValidity;
        }

        var aliasIsEVMAddressValidity = validateAliasIsEVMAddressCase(op);
        if (aliasIsEVMAddressValidity != OK) {
            return aliasIsEVMAddressValidity;
        }

        if (op.getInitialBalance() < 0L) {
            return INVALID_INITIAL_BALANCE;
        }
        if (!op.hasAutoRenewPeriod()) {
            return INVALID_RENEWAL_PERIOD;
        }
        if (!validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod())) {
            return AUTORENEW_DURATION_NOT_IN_RANGE;
        }
        if (op.getSendRecordThreshold() < 0L) {
            return INVALID_SEND_RECORD_THRESHOLD;
        }
        if (op.getReceiveRecordThreshold() < 0L) {
            return INVALID_RECEIVE_RECORD_THRESHOLD;
        }
        if (tooManyAutoAssociations(op.getMaxAutomaticTokenAssociations())) {
            return REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
        }
        if (op.hasProxyAccountID()
                && !op.getProxyAccountID().equals(AccountID.getDefaultInstance())) {
            return PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
        }
        final var stakedIdCase = op.getStakedIdCase().name();
        final var electsStakingId = hasStakedId(stakedIdCase);
        if (!dynamicProperties.isStakingEnabled() && (electsStakingId || op.getDeclineReward())) {
            return STAKING_NOT_ENABLED;
        }
        if (electsStakingId
                && !validator.isValidStakedId(
                        stakedIdCase,
                        op.getStakedAccountId(),
                        op.getStakedNodeId(),
                        accounts.get(),
                        nodeInfo)) {
            return INVALID_STAKING_ID;
        }
        return OK;
    }

    private ResponseCodeEnum validateNoAliasCase(final CryptoCreateTransactionBody op) {
        if (op.getAlias().isEmpty() || op.hasKey()) {
            if (!op.hasKey()) {
                return KEY_REQUIRED;
            }

            final var keyValidity = validateKey(op);
            if (keyValidity != OK) {
                return keyValidity;
            }

            if (!op.getKey().getECDSASecp256K1().isEmpty()) {
                var isKeyUsedAsAliasValidity = isUsedAsAliasCheck(op.getKey().getECDSASecp256K1());

                if (isKeyUsedAsAliasValidity != OK) {
                    return isKeyUsedAsAliasValidity;
                }

                return tryToRecoverEVMAddressAndCheckValidity(
                        op.getKey().getECDSASecp256K1().toByteArray());
            }
        }
        return OK;
    }

    private ResponseCodeEnum validateAliasBiggerThanEVMAddressSizeCase(
            final CryptoCreateTransactionBody op) {
        var alias = op.getAlias();
        if (!alias.isEmpty() && alias.size() > EVM_ADDRESS_SIZE) {
            if (!dynamicProperties.isCryptoCreateWithAliasEnabled()) {
                return NOT_SUPPORTED;
            }

            var isAliasUsedCheck = isUsedAsAliasCheck(alias);

            if (isAliasUsedCheck != OK) {
                return isAliasUsedCheck;
            }

            var keyFromAlias = asPrimitiveKeyUnchecked(alias);
            var key = op.getKey();
            if ((!key.getEd25519().isEmpty() || !key.getECDSASecp256K1().isEmpty())
                    && !key.equals(keyFromAlias)) {
                return INVALID_ALIAS_KEY;
            }

            if (!keyFromAlias.getECDSASecp256K1().isEmpty()) {

                return tryToRecoverEVMAddressAndCheckValidity(
                        keyFromAlias.getECDSASecp256K1().toByteArray());
            }
        }
        return OK;
    }

    private ResponseCodeEnum validateAliasIsEVMAddressCase(final CryptoCreateTransactionBody op) {
        var alias = op.getAlias();
        if (!alias.isEmpty() && alias.size() == EVM_ADDRESS_SIZE) {
            if (!dynamicProperties.isCryptoCreateWithAliasEnabled()
                    || !dynamicProperties.isLazyCreationEnabled()) {
                return NOT_SUPPORTED;
            }

            var isAliasUsedCheck = isUsedAsAliasCheck(alias);

            if (isAliasUsedCheck != OK) {
                return isAliasUsedCheck;
            }

            if (op.hasKey()) {
                final var ecdsaKey = op.getKey().getECDSASecp256K1();
                if (ecdsaKey.isEmpty()) {
                    return INVALID_ADMIN_KEY;
                }
                final var recoveredEvmAddress =
                        recoverAddressFromPubKey(op.getKey().getECDSASecp256K1().toByteArray());
                if (!Arrays.equals(recoveredEvmAddress, alias.toByteArray())) {
                    return INVALID_ALIAS_KEY;
                }
            }
        }
        return OK;
    }

    private ResponseCodeEnum tryToRecoverEVMAddressAndCheckValidity(final byte[] key) {
        var recoveredEVMAddress = recoverAddressFromPubKey(key);
        if (recoveredEVMAddress != null) {
            return isUsedAsAliasCheck(ByteString.copyFrom(recoveredEVMAddress));
        }
        return OK;
    }

    private ResponseCodeEnum validateKey(final CryptoCreateTransactionBody op) {
        if (!validator.hasGoodEncoding(op.getKey())) {
            return BAD_ENCODING;
        }
        var fcKey = asFcKeyUnchecked(op.getKey());
        if (fcKey.isEmpty()) {
            return KEY_REQUIRED;
        }
        if (!fcKey.isValid()) {
            return INVALID_ADMIN_KEY;
        }
        return OK;
    }

    private boolean tooManyAutoAssociations(final int n) {
        return n > MAX_CHARGEABLE_AUTO_ASSOCIATIONS
                || (dynamicProperties.areTokenAssociationsLimited()
                        && n > dynamicProperties.maxTokensPerAccount());
    }
}
