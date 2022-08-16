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
package com.hedera.services.txns.validation;

import static com.hedera.services.ledger.accounts.HederaAccountCustomizer.STAKED_ACCOUNT_ID_CASE;
import static com.hedera.services.utils.EntityNum.fromContractId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_START;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;

import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import java.util.Optional;
import org.apache.commons.codec.DecoderException;

public final class PureValidation {
    private PureValidation() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static ResponseCodeEnum queryableFileStatus(final FileID id, final StateView view) {
        final var info = view.infoForFile(id);
        if (info.isEmpty()) {
            return INVALID_FILE_ID;
        } else {
            return OK;
        }
    }

    public static ResponseCodeEnum queryableAccountOrContractStatus(
            final EntityNum entityNum, final MerkleMap<EntityNum, MerkleAccount> accounts) {
        return internalQueryableAccountStatus(true, entityNum, accounts);
    }

    public static ResponseCodeEnum queryableAccountStatus(
            final EntityNum entityNum, final MerkleMap<EntityNum, MerkleAccount> accounts) {
        return internalQueryableAccountStatus(false, entityNum, accounts);
    }

    private static ResponseCodeEnum internalQueryableAccountStatus(
            final boolean contractIsOk,
            final EntityNum entityNum,
            final MerkleMap<EntityNum, MerkleAccount> accounts) {
        final var account = accounts.get(entityNum);

        return Optional.ofNullable(account)
                .map(
                        v -> {
                            if (v.isDeleted()) {
                                return ACCOUNT_DELETED;
                            }
                            return (contractIsOk || !v.isSmartContract()) ? OK : INVALID_ACCOUNT_ID;
                        })
                .orElse(INVALID_ACCOUNT_ID);
    }

    public static ResponseCodeEnum queryableContractStatus(
            final ContractID cid, final MerkleMap<EntityNum, MerkleAccount> contracts) {
        return queryableContractStatus(fromContractId(cid), contracts);
    }

    public static ResponseCodeEnum queryableContractStatus(
            final EntityNum contractId, final MerkleMap<EntityNum, MerkleAccount> contracts) {
        final var contract = contracts.get(contractId);

        return Optional.ofNullable(contract)
                .map(
                        v -> {
                            if (v.isDeleted()) {
                                return CONTRACT_DELETED;
                            }
                            return !v.isSmartContract() ? INVALID_CONTRACT_ID : OK;
                        })
                .orElse(INVALID_CONTRACT_ID);
    }

    public static ResponseCodeEnum chronologyStatus(
            final Instant consensusTime, final Instant validStart, long validDuration) {
        validDuration =
                Math.min(validDuration, Instant.MAX.getEpochSecond() - validStart.getEpochSecond());
        if (validStart.plusSeconds(validDuration).isBefore(consensusTime)) {
            return TRANSACTION_EXPIRED;
        } else if (!validStart.isBefore(consensusTime)) {
            return INVALID_TRANSACTION_START;
        } else {
            return OK;
        }
    }

    public static Instant asCoercedInstant(final Timestamp when) {
        return Instant.ofEpochSecond(
                Math.min(
                        Math.max(Instant.MIN.getEpochSecond(), when.getSeconds()),
                        Instant.MAX.getEpochSecond()),
                Math.min(Math.max(Instant.MIN.getNano(), when.getNanos()), Instant.MAX.getNano()));
    }

    public static ResponseCodeEnum checkKey(final Key key, final ResponseCodeEnum failure) {
        try {
            final var fcKey = JKey.mapKey(key);
            if (!fcKey.isValid()) {
                return failure;
            }
            return OK;
        } catch (DecoderException ignore) {
            return failure;
        }
    }

    /**
     * Validates if the stakedNodeId set in the CryptoCreate, CryptoUpdate, ContractCreate and
     * ContractUpdate operations is valid.
     *
     * @param idCase case if staked account id or staked node id is set
     * @param stakedAccountId given staked account id
     * @param stakedNodeId given staked node id
     * @param accounts account map
     * @param nodeInfo node Info object
     * @return true if valid, false otherwise
     */
    public static boolean isValidStakedId(
            final String idCase,
            final AccountID stakedAccountId,
            final long stakedNodeId,
            final MerkleMap<EntityNum, MerkleAccount> accounts,
            final NodeInfo nodeInfo) {
        if (idCase.matches(STAKED_ACCOUNT_ID_CASE)) {
            return queryableAccountOrContractStatus(
                            EntityNum.fromAccountId(stakedAccountId), accounts)
                    == OK;
        } else {
            return nodeInfo.isValidId(stakedNodeId);
        }
    }
}
