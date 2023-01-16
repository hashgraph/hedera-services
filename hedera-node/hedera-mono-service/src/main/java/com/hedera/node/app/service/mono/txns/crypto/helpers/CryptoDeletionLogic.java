/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.txns.crypto.helpers;

import static com.hedera.node.app.service.mono.exceptions.ValidationUtils.validateFalse;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT;

import com.hedera.node.app.service.mono.ledger.HederaLedger;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import javax.inject.Inject;

public class CryptoDeletionLogic {
    private final HederaLedger ledger;
    private final SigImpactHistorian sigImpactHistorian;
    private final AliasManager aliasManager;

    private AccountID beneficiary;

    @Inject
    public CryptoDeletionLogic(
            final HederaLedger ledger,
            final SigImpactHistorian sigImpactHistorian,
            final AliasManager aliasManager) {
        this.ledger = ledger;
        this.sigImpactHistorian = sigImpactHistorian;
        this.aliasManager = aliasManager;
    }

    public AccountID performCryptoDeleteFor(final CryptoDeleteTransactionBody op) {
        beneficiary = null;
        AccountID id = op.getDeleteAccountID();
        validateFalse(ledger.isKnownTreasury(id), ACCOUNT_IS_TREASURY);

        beneficiary = op.getTransferAccountID();
        validateFalse(ledger.isDetached(id), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
        validateFalse(ledger.isDetached(beneficiary), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

        ledger.delete(id, beneficiary);
        sigImpactHistorian.markEntityChanged(id.getAccountNum());

        final var aliasIfAny = ledger.alias(id);
        if (!aliasIfAny.isEmpty()) {
            ledger.clearAlias(id);
            aliasManager.unlink(aliasIfAny);
            sigImpactHistorian.markAliasChanged(aliasIfAny);
        }
        return id;
    }

    public ResponseCodeEnum validate(CryptoDeleteTransactionBody op) {
        if (!op.hasDeleteAccountID() || !op.hasTransferAccountID()) {
            return ACCOUNT_ID_DOES_NOT_EXIST;
        }

        if (op.getDeleteAccountID().equals(op.getTransferAccountID())) {
            return TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT;
        }

        return OK;
    }

    public AccountID getLastBeneficiary() {
        return beneficiary;
    }
}
