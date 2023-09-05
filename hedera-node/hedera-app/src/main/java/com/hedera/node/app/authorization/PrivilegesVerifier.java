/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.authorization;

import static com.hedera.node.app.authorization.Authorizer.SystemPrivilege.AUTHORIZED;
import static com.hedera.node.app.authorization.Authorizer.SystemPrivilege.IMPERMISSIBLE;
import static com.hedera.node.app.authorization.Authorizer.SystemPrivilege.UNAUTHORIZED;
import static com.hedera.node.app.authorization.Authorizer.SystemPrivilege.UNNECESSARY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.file.SystemDeleteTransactionBody;
import com.hedera.hapi.node.file.SystemUndeleteTransactionBody;
import com.hedera.hapi.node.token.CryptoUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.authorization.Authorizer.SystemPrivilege;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.LedgerConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;

/**
 * Checks whether an account is authorized to perform a system transaction that requires
 * privileged access.
 */
public class PrivilegesVerifier {


    private final AccountsConfig accountsConfig;
    private final FilesConfig filesConfig;
    private final long numReservedSystemEntities;

    @Inject
    public PrivilegesVerifier(@NonNull final ConfigProvider configProvider) {
        requireNonNull(configProvider, "configProvider cannot be null");

        final var configuration = configProvider.getConfiguration();
        this.accountsConfig = configuration.getConfigData(AccountsConfig.class);
        this.filesConfig = configuration.getConfigData(FilesConfig.class);
        this.numReservedSystemEntities = configuration.getConfigData(LedgerConfig.class)
                .numReservedSystemEntities();
    }

    /**
     * Checks whether an account is exempt from paying fees.
     *
     * @param accountID the {@link AccountID} to check
     * @param functionality the {@link HederaFunctionality} of the transaction
     * @param txBody the {@link TransactionBody} of the transaction
     * @return {@code true} if the account is exempt from paying fees, otherwise {@code false}
     */
    public SystemPrivilege hasPrivileges(
            @NonNull final AccountID accountID,
            @NonNull final HederaFunctionality functionality,
            @NonNull final TransactionBody txBody) {
        return switch (functionality) {
            // Authorization privileges for special transactions
            case FREEZE -> checkFreeze(accountID);
            case SYSTEM_DELETE -> checkSystemDelete(accountID, txBody.systemDeleteOrThrow());
            case SYSTEM_UNDELETE -> checkSystemUndelete(accountID, txBody.systemUndeleteOrThrow());
            case UNCHECKED_SUBMIT -> checkUncheckedSubmit(accountID);

            // Authorization privileges for file updates and appends
            case FILE_UPDATE -> checkFileChange(accountID, txBody.fileUpdateOrThrow().fileIDOrThrow().fileNum());
            case FILE_APPEND -> checkFileChange(accountID, txBody.fileAppendOrThrow().fileIDOrThrow().fileNum());
            case CONTRACT_UPDATE -> checkFileChange(accountID, txBody.contractUpdateInstanceOrThrow().contractIDOrThrow().contractNumOrThrow());

            // Authorization for crypto updates
            case CRYPTO_UPDATE -> checkCryptoUpdate(accountID, txBody.cryptoUpdateAccountOrThrow());

            // Authorization for deletes
            case FILE_DELETE -> checkEntityDelete(txBody.fileDeleteOrThrow().fileIDOrThrow().fileNum());
            case CRYPTO_DELETE -> checkEntityDelete(txBody.cryptoDeleteOrThrow().deleteAccountIDOrThrow().accountNumOrThrow());
            case CONTRACT_DELETE -> checkEntityDelete(txBody.contractDeleteInstanceOrThrow().contractIDOrThrow().contractNumOrThrow());

            default -> SystemPrivilege.UNNECESSARY;
        };
    }

    private boolean isSystemEntity(final long entityNum) {
        return 1 <= entityNum && entityNum <= numReservedSystemEntities;
    }

    private boolean isSuperUser(@NonNull final AccountID accountID) {
        final long accountNum = accountID.accountNumOrThrow();
        return accountNum == accountsConfig.treasury() || accountNum == accountsConfig.systemAdmin();
    }

    private boolean isTreasury(@NonNull final AccountID accountID) {
        return accountID.accountNumOrThrow() == accountsConfig.treasury();
    }

    private boolean isSystemAdmin(@NonNull final AccountID accountID) {
        return accountID.accountNumOrThrow() == accountsConfig.systemAdmin();
    }

    private boolean hasFreezePrivilege(@NonNull final AccountID accountID) {
        return accountID.accountNumOrThrow() == accountsConfig.freezeAdmin() || isSuperUser(accountID);
    }

    private boolean hasSystemDeletePrivilege(@NonNull final AccountID accountID) {
        return accountID.accountNumOrThrow() == accountsConfig.systemDeleteAdmin() || isSuperUser(accountID);
    }

    private boolean hasSystemUndeletePrivilege(@NonNull final AccountID accountID) {
        return accountID.accountNumOrThrow() == accountsConfig.systemUndeleteAdmin() || isSuperUser(accountID);
    }

    private boolean hasAddressBookPrivilege(@NonNull final AccountID accountID) {
        return accountID.accountNumOrThrow() == accountsConfig.addressBookAdmin() || isSuperUser(accountID);
    }

    private boolean hasExchangeRatePrivilige(@NonNull final AccountID accountID) {
        return accountID.accountNumOrThrow() == accountsConfig.exchangeRatesAdmin() || isSuperUser(accountID);
    }

    private boolean hasFeeSchedulePrivilige(@NonNull final AccountID accountID) {
        return accountID.accountNumOrThrow() == accountsConfig.feeSchedulesAdmin() || isSuperUser(accountID);
    }

    private SystemPrivilege checkFreeze(@NonNull final AccountID accountID) {
        return hasFreezePrivilege(accountID) ? AUTHORIZED : UNAUTHORIZED;
    }

    private SystemPrivilege checkSystemDelete(@NonNull final AccountID accountID, @NonNull final SystemDeleteTransactionBody op) {
        final var entityNum = op.hasFileID()? op.fileIDOrThrow().fileNum() : op.contractIDOrThrow().contractNumOrThrow();
        if (isSystemEntity(entityNum)) {
            return IMPERMISSIBLE;
        }
        return hasSystemDeletePrivilege(accountID) ? AUTHORIZED : UNAUTHORIZED;
    }

    private SystemPrivilege checkSystemUndelete(@NonNull final AccountID accountID, @NonNull final SystemUndeleteTransactionBody op) {
        final var entityNum = op.hasFileID()? op.fileIDOrThrow().fileNum() : op.contractIDOrThrow().contractNumOrThrow();
        if (isSystemEntity(entityNum)) {
            return IMPERMISSIBLE;
        }
        return hasSystemUndeletePrivilege(accountID) ? AUTHORIZED : UNAUTHORIZED;
    }

    private SystemPrivilege checkUncheckedSubmit(@NonNull final AccountID accountID) {
        return isSuperUser(accountID) ? AUTHORIZED : UNAUTHORIZED;
    }

    private SystemPrivilege checkFileChange(@NonNull final AccountID accountID, final long entityNum) {
        if (!isSystemEntity(entityNum)) {
            return UNNECESSARY;
        }
        if (entityNum == filesConfig.addressBook() || entityNum == filesConfig.nodeDetails()) {
            return hasAddressBookPrivilege(accountID) ? AUTHORIZED : UNAUTHORIZED;
        } else if (entityNum == filesConfig.networkProperties() || entityNum == filesConfig.hapiPermissions()) {
            return hasAddressBookPrivilege(accountID) || hasExchangeRatePrivilige(accountID) ? AUTHORIZED : UNAUTHORIZED;
        } else if (entityNum == filesConfig.feeSchedules()) {
            return hasFeeSchedulePrivilige(accountID) ? AUTHORIZED : UNAUTHORIZED;
        } else if (entityNum == filesConfig.exchangeRates()) {
            return hasExchangeRatePrivilige(accountID) ? AUTHORIZED : UNAUTHORIZED;
        } else if (filesConfig.softwareUpdateRange().left() <= entityNum && entityNum <= filesConfig.softwareUpdateRange().right()) {
            return hasFreezePrivilege(accountID) ? AUTHORIZED : UNAUTHORIZED;
        } else if (entityNum == filesConfig.throttleDefinitions()) {
            return hasAddressBookPrivilege(accountID) || hasExchangeRatePrivilige(accountID) ? AUTHORIZED : UNAUTHORIZED;
        }
        return UNAUTHORIZED;
    }

    private SystemPrivilege checkCryptoUpdate(
            @NonNull final AccountID accountID,
            @NonNull final CryptoUpdateTransactionBody op) {
        final long targetNum = op.accountIDToUpdateOrThrow().accountNumOrThrow();
        if (!isSystemEntity(targetNum)) {
            return UNNECESSARY;
        }
        if (targetNum == accountsConfig.treasury()) {
            return isTreasury(accountID)? AUTHORIZED : UNAUTHORIZED;
        } else {
            return isSuperUser(accountID)? AUTHORIZED : UNNECESSARY;
        }
    }

    private SystemPrivilege checkEntityDelete(final long entityNum) {
        return isSystemEntity(entityNum)? IMPERMISSIBLE : UNNECESSARY;
    }
}
