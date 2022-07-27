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
package com.hedera.services.txns.auth;

import static com.hedera.services.txns.auth.SystemOpAuthorization.AUTHORIZED;
import static com.hedera.services.txns.auth.SystemOpAuthorization.IMPERMISSIBLE;
import static com.hedera.services.txns.auth.SystemOpAuthorization.UNAUTHORIZED;
import static com.hedera.services.txns.auth.SystemOpAuthorization.UNNECESSARY;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.Freeze;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemUndelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UncheckedSubmit;

import com.hedera.services.config.EntityNumbers;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.EnumMap;
import java.util.Optional;
import java.util.function.BiFunction;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SystemOpPolicies {
    private final EntityNumbers entityNums;

    private final EnumMap<
                    HederaFunctionality,
                    BiFunction<TransactionBody, AccountID, SystemOpAuthorization>>
            functionPolicies;

    @Inject
    public SystemOpPolicies(final EntityNumbers entityNums) {
        this.entityNums = entityNums;

        functionPolicies = new EnumMap<>(HederaFunctionality.class);

        functionPolicies.put(FileDelete, this::checkFileDelete);
        functionPolicies.put(CryptoDelete, this::checkCryptoDelete);
        functionPolicies.put(ContractDelete, this::checkContractDelete);

        functionPolicies.put(CryptoUpdate, this::checkCryptoUpdate);
        functionPolicies.put(ContractUpdate, this::checkContractUpdate);
        functionPolicies.put(FileUpdate, this::checkFileUpdate);
        functionPolicies.put(FileAppend, this::checkFileAppend);

        functionPolicies.put(Freeze, this::checkFreeze);
        functionPolicies.put(SystemDelete, this::checkSystemDelete);
        functionPolicies.put(SystemUndelete, this::checkSystemUndelete);

        functionPolicies.put(UncheckedSubmit, this::checkUncheckedSubmit);

        functionPolicies.put(EthereumTransaction, this::checkEthereumTransaction);
    }

    public SystemOpAuthorization checkAccessor(final TxnAccessor accessor) {
        return checkKnownTxn(accessor.getTxn(), accessor.getFunction(), accessor.getPayer());
    }

    public SystemOpAuthorization checkKnownTxn(
            final TransactionBody txn, final HederaFunctionality function, final AccountID payer) {
        return Optional.ofNullable(functionPolicies.get(function))
                .map(opCheck -> opCheck.apply(txn, payer))
                .orElse(UNNECESSARY);
    }

    private SystemOpAuthorization checkUncheckedSubmit(
            final TransactionBody txn, final AccountID payer) {
        return entityNums.accounts().isSuperuser(payerFor(txn, payer)) ? AUTHORIZED : UNAUTHORIZED;
    }

    private SystemOpAuthorization checkEthereumTransaction(
            final TransactionBody txn, final AccountID payer) {
        // Apply more fine-tuned logic
        return UNNECESSARY;
    }

    private SystemOpAuthorization checkSystemUndelete(
            final TransactionBody txn, final AccountID payer) {
        final var op = txn.getSystemUndelete();
        final long thePayer = payerFor(txn, payer);
        if (op.hasFileID()) {
            return checkSystemUndeleteFile(thePayer, op.getFileID());
        } else {
            return checkSystemUndeleteContract(thePayer, op.getContractID());
        }
    }

    private SystemOpAuthorization checkSystemDelete(
            final TransactionBody txn, final AccountID payer) {
        final var op = txn.getSystemDelete();
        final long thePayer = payerFor(txn, payer);
        if (op.hasFileID()) {
            return checkSystemDeleteFile(thePayer, op.getFileID());
        } else {
            return checkSystemDeleteContract(thePayer, op.getContractID());
        }
    }

    private SystemOpAuthorization checkSystemUndeleteFile(
            final long payerAccount, final FileID id) {
        if (entityNums.isSystemFile(id)) {
            return IMPERMISSIBLE;
        }
        return hasSysUndelPrivileges(payerAccount) ? AUTHORIZED : UNAUTHORIZED;
    }

    private SystemOpAuthorization checkSystemUndeleteContract(
            final long payerAccount, final ContractID id) {
        if (entityNums.isSystemContract(id)) {
            return IMPERMISSIBLE;
        }
        return hasSysUndelPrivileges(payerAccount) ? AUTHORIZED : UNAUTHORIZED;
    }

    private SystemOpAuthorization checkSystemDeleteFile(final long payerAccount, final FileID id) {
        if (entityNums.isSystemFile(id)) {
            return IMPERMISSIBLE;
        }
        return hasSysDelPrivileges(payerAccount) ? AUTHORIZED : UNAUTHORIZED;
    }

    private SystemOpAuthorization checkSystemDeleteContract(
            final long payerAccount, final ContractID id) {
        if (entityNums.isSystemContract(id)) {
            return IMPERMISSIBLE;
        }
        return hasSysDelPrivileges(payerAccount) ? AUTHORIZED : UNAUTHORIZED;
    }

    private boolean hasSysDelPrivileges(final long payerAccount) {
        return entityNums.accounts().isSuperuser(payerAccount)
                || payerAccount == entityNums.accounts().systemDeleteAdmin();
    }

    private boolean hasSysUndelPrivileges(final long payerAccount) {
        return entityNums.accounts().isSuperuser(payerAccount)
                || payerAccount == entityNums.accounts().systemUndeleteAdmin();
    }

    private SystemOpAuthorization checkFreeze(final TransactionBody txn, final AccountID payer) {
        final var thePayer = payerFor(txn, payer);
        final boolean isAuthorized =
                thePayer == entityNums.accounts().treasury()
                        || thePayer == entityNums.accounts().systemAdmin()
                        || thePayer == entityNums.accounts().freezeAdmin();
        return isAuthorized ? AUTHORIZED : UNAUTHORIZED;
    }

    private SystemOpAuthorization checkContractUpdate(
            final TransactionBody txn, final AccountID payer) {
        final var target = txn.getContractUpdateInstance().getContractID();
        if (!entityNums.isSystemContract(target)) {
            return UNNECESSARY;
        }
        return canPerformNonCryptoUpdate(payerFor(txn, payer), target.getContractNum())
                ? AUTHORIZED
                : UNAUTHORIZED;
    }

    private SystemOpAuthorization checkFileUpdate(
            final TransactionBody txn, final AccountID payer) {
        final var target = txn.getFileUpdate().getFileID();
        if (!entityNums.isSystemFile(target)) {
            return UNNECESSARY;
        }
        return canPerformNonCryptoUpdate(payerFor(txn, payer), target.getFileNum())
                ? AUTHORIZED
                : UNAUTHORIZED;
    }

    private SystemOpAuthorization checkFileAppend(
            final TransactionBody txn, final AccountID payer) {
        final var target = txn.getFileAppend().getFileID();
        if (!entityNums.isSystemFile(target)) {
            return UNNECESSARY;
        }
        return canPerformNonCryptoUpdate(payerFor(txn, payer), target.getFileNum())
                ? AUTHORIZED
                : UNAUTHORIZED;
    }

    private SystemOpAuthorization checkCryptoUpdate(
            final TransactionBody txn, final AccountID payer) {
        final var target = txn.getCryptoUpdateAccount().getAccountIDToUpdate();
        if (!entityNums.isSystemAccount(target)) {
            return UNNECESSARY;
        } else {
            final var thePayer = payerFor(txn, payer);
            if (thePayer == entityNums.accounts().treasury()) {
                return AUTHORIZED;
            } else if (thePayer == entityNums.accounts().systemAdmin()) {
                return (target.getAccountNum() == entityNums.accounts().treasury())
                        ? UNAUTHORIZED
                        : AUTHORIZED;
            } else {
                return (target.getAccountNum() == entityNums.accounts().treasury())
                        ? UNAUTHORIZED
                        : UNNECESSARY;
            }
        }
    }

    private SystemOpAuthorization checkContractDelete(
            final TransactionBody txn, final AccountID payer) {
        return entityNums.isSystemContract(txn.getContractDeleteInstance().getContractID())
                ? IMPERMISSIBLE
                : UNNECESSARY;
    }

    private SystemOpAuthorization checkCryptoDelete(
            final TransactionBody txn, final AccountID payer) {
        return entityNums.isSystemAccount(txn.getCryptoDelete().getDeleteAccountID())
                ? IMPERMISSIBLE
                : UNNECESSARY;
    }

    private SystemOpAuthorization checkFileDelete(
            final TransactionBody txn, final AccountID payer) {
        return entityNums.isSystemFile(txn.getFileDelete().getFileID())
                ? IMPERMISSIBLE
                : UNNECESSARY;
    }

    private long payerFor(final TransactionBody txn, final AccountID payer) {
        return (payer == null ? txn.getTransactionID().getAccountID() : payer).getAccountNum();
    }

    boolean canPerformNonCryptoUpdate(final long payer, final long nonAccountSystemEntity) {
        if (payer == entityNums.accounts().treasury()
                || payer == entityNums.accounts().systemAdmin()) {
            return true;
        } else if (payer == entityNums.accounts().addressBookAdmin()) {
            return canAddressBookAdminUpdate(nonAccountSystemEntity);
        } else if (payer == entityNums.accounts().exchangeRatesAdmin()) {
            return canExchangeRatesAdminUpdate(nonAccountSystemEntity);
        } else if (payer == entityNums.accounts().feeSchedulesAdmin()) {
            return nonAccountSystemEntity == entityNums.files().feeSchedules();
        } else if (payer == entityNums.accounts().freezeAdmin()) {
            return canFreezeAdminUpdate(nonAccountSystemEntity);
        } else {
            return false;
        }
    }

    private boolean canFreezeAdminUpdate(final long entity) {
        return entityNums.files().firstSoftwareUpdateFile() <= entity
                && entity <= entityNums.files().lastSoftwareUpdateFile();
    }

    private boolean canExchangeRatesAdminUpdate(final long entity) {
        return entity == entityNums.files().exchangeRates()
                || entity == entityNums.files().throttleDefinitions()
                || isPropertiesOrPermissions(entity);
    }

    private boolean canAddressBookAdminUpdate(final long entity) {
        return entity == entityNums.files().addressBook()
                || entity == entityNums.files().nodeDetails()
                || entity == entityNums.files().throttleDefinitions()
                || isPropertiesOrPermissions(entity);
    }

    private boolean isPropertiesOrPermissions(final long entity) {
        return entity == entityNums.files().applicationProperties()
                || entity == entityNums.files().apiPermissions();
    }
}
