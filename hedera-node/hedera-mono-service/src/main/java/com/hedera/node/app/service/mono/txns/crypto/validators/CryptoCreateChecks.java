/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.txns.crypto.validators;

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.node.app.service.mono.ledger.accounts.HederaAccountCustomizer.hasStakedId;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.EVM_ADDRESS_SIZE;
import static com.hedera.node.app.service.mono.utils.EntityNum.MISSING_NUM;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asPrimitiveKeyUnchecked;
import static com.hedera.node.app.service.mono.utils.MiscUtils.isSerializedProtoKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ALIAS_ALREADY_ASSIGNED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_INITIAL_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RECEIVE_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SEND_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STAKING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_REQUIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.STAKING_NOT_ENABLED;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.node.app.service.mono.context.NodeInfo;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CryptoCreateChecks {
    public static final int MAX_CHARGEABLE_AUTO_ASSOCIATIONS = 5000;
    private final GlobalDynamicProperties dynamicProperties;
    private final OptionValidator validator;
    private final Supplier<AccountStorageAdapter> accounts;
    private final NodeInfo nodeInfo;
    private final AliasManager aliasManager;

    @Inject
    public CryptoCreateChecks(
            final GlobalDynamicProperties dynamicProperties,
            final OptionValidator validator,
            final Supplier<AccountStorageAdapter> accounts,
            final NodeInfo nodeInfo,
            final AliasManager aliasManager) {
        this.dynamicProperties = dynamicProperties;
        this.validator = validator;
        this.accounts = accounts;
        this.nodeInfo = nodeInfo;
        this.aliasManager = aliasManager;
    }

    @SuppressWarnings("java:S1874")
    public ResponseCodeEnum cryptoCreateValidation(final CryptoCreateTransactionBody op) {
        final var memoValidity = validator.memoCheck(op.getMemo());
        if (memoValidity != OK) {
            return memoValidity;
        }
        final var keyAliasAndEvmAddressCombinationsValidity = validateKeyAliasAndEvmAddressCombinations(op);
        if (keyAliasAndEvmAddressCombinationsValidity != OK) {
            return keyAliasAndEvmAddressCombinationsValidity;
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
        if (op.hasProxyAccountID() && !op.getProxyAccountID().equals(AccountID.getDefaultInstance())) {
            return PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
        }
        final var stakedIdCase = op.getStakedIdCase().name();
        final var electsStakingId = hasStakedId(stakedIdCase);
        if (!dynamicProperties.isStakingEnabled() && (electsStakingId || op.getDeclineReward())) {
            return STAKING_NOT_ENABLED;
        }
        if (electsStakingId
                && !validator.isValidStakedId(
                        stakedIdCase, op.getStakedAccountId(), op.getStakedNodeId(), accounts.get(), nodeInfo)) {
            return INVALID_STAKING_ID;
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
                || (dynamicProperties.areTokenAssociationsLimited() && n > dynamicProperties.maxTokensPerAccount());
    }

    private ResponseCodeEnum validateKeyAliasAndEvmAddressCombinations(final CryptoCreateTransactionBody op) {
        if (onlyKeyProvided(op)) {
            return validateKey(op);
        } else if (onlyAliasProvided(op)) {
            return validateOnlyAliasProvidedCase(op);
        } else if (keyAndAliasProvided(op)) {
            return validateKeyAndAliasProvidedCase(op);
        } else {
            // This is the case when no key and no alias are provided
            return KEY_REQUIRED;
        }
    }

    public static boolean onlyKeyProvided(CryptoCreateTransactionBody op) {
        return op.hasKey() && op.getAlias().isEmpty();
    }

    public static boolean keyAndAliasProvided(CryptoCreateTransactionBody op) {
        return op.hasKey() && !op.getAlias().isEmpty();
    }

    public static boolean onlyAliasProvided(CryptoCreateTransactionBody op) {
        return !op.hasKey() && !op.getAlias().isEmpty();
    }

    private ResponseCodeEnum validateKeyAndAliasProvidedCase(final CryptoCreateTransactionBody op) {
        if (!dynamicProperties.isCryptoCreateWithAliasEnabled()) {
            return NOT_SUPPORTED;
        }
        final var keyValidity = validateKey(op);
        if (keyValidity != OK) {
            return keyValidity;
        }
        final var alias = op.getAlias();
        if (alias.size() == EVM_ADDRESS_SIZE) {
            return validateEvmAddressAlias(alias);
        } else {
            return validatePublicKeyAlias(alias);
        }
    }

    private ResponseCodeEnum validateOnlyAliasProvidedCase(final CryptoCreateTransactionBody op) {
        if (!dynamicProperties.isCryptoCreateWithAliasEnabled()) {
            return NOT_SUPPORTED;
        }
        final var alias = op.getAlias();
        if (alias.size() == EVM_ADDRESS_SIZE) {
            return INVALID_ALIAS_KEY;
        } else {
            return validatePublicKeyAlias(alias);
        }
    }

    private ResponseCodeEnum validatePublicKeyAlias(final ByteString alias) {
        if (!isSerializedProtoKey(alias)) {
            return INVALID_ALIAS_KEY;
        }
        final var isAliasUsedCheck = isUsedAsAliasCheck(alias);
        if (isAliasUsedCheck != OK) {
            return isAliasUsedCheck;
        }
        final var keyFromAlias = asPrimitiveKeyUnchecked(alias);
        if (!keyFromAlias.getECDSASecp256K1().isEmpty()) {
            return tryToRecoverEVMAddressAndCheckValidity(
                    keyFromAlias.getECDSASecp256K1().toByteArray());
        }
        return OK;
    }

    public ResponseCodeEnum validateEvmAddressAlias(final ByteString alias) {
        if (HederaEvmContractAliases.isMirror(alias.toByteArray())) {
            return INVALID_ALIAS_KEY;
        }
        return isUsedAsAliasCheck(alias);
    }

    private ResponseCodeEnum isUsedAsAliasCheck(final ByteString alias) {
        return aliasManager.lookupIdBy(alias).equals(MISSING_NUM) ? OK : ALIAS_ALREADY_ASSIGNED;
    }

    private ResponseCodeEnum tryToRecoverEVMAddressAndCheckValidity(final byte[] key) {
        final var recoveredEVMAddress = recoverAddressFromPubKey(key);
        return isUsedAsAliasCheck(ByteString.copyFrom(recoveredEVMAddress));
    }
}
