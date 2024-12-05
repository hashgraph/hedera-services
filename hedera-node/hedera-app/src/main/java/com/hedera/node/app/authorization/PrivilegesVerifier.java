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

package com.hedera.node.app.authorization;

import static com.hedera.node.app.spi.authorization.SystemPrivilege.AUTHORIZED;
import static com.hedera.node.app.spi.authorization.SystemPrivilege.IMPERMISSIBLE;
import static com.hedera.node.app.spi.authorization.SystemPrivilege.UNAUTHORIZED;
import static com.hedera.node.app.spi.authorization.SystemPrivilege.UNNECESSARY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.file.SystemDeleteTransactionBody;
import com.hedera.hapi.node.file.SystemUndeleteTransactionBody;
import com.hedera.hapi.node.token.CryptoUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.authorization.SystemPrivilege;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.LedgerConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;

/**
 * Checks whether an account is authorized to perform a system transaction that requires
 * privileged access.
 *
 * <p>The checks in this class do not require access to state, and thus can be performed at any time.
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
        this.numReservedSystemEntities =
                configuration.getConfigData(LedgerConfig.class).numReservedSystemEntities();
    }

    /**
     * Checks whether an account is exempt from paying fees.
     *
     * @param payerId the payer {@link AccountID} for the transaction
     * @param functionality the {@link HederaFunctionality} of the transaction
     * @param txBody the {@link TransactionBody} of the transaction
     * @return {@code true} if the account is exempt from paying fees, otherwise {@code false}
     */
    public SystemPrivilege hasPrivileges(
            @NonNull final AccountID payerId,
            @NonNull final HederaFunctionality functionality,
            @NonNull final TransactionBody txBody) {
        return switch (functionality) {
                // Authorization privileges for special transactions
            case FREEZE -> checkFreeze(payerId);
            case SYSTEM_DELETE -> checkSystemDelete(payerId, txBody.systemDeleteOrThrow());
            case SYSTEM_UNDELETE -> checkSystemUndelete(payerId, txBody.systemUndeleteOrThrow());
            case UNCHECKED_SUBMIT -> checkUncheckedSubmit(payerId);

                // Authorization privileges for file updates and appends
            case FILE_UPDATE -> checkFileChange(
                    payerId, txBody.fileUpdateOrThrow().fileIDOrThrow().fileNum());
            case FILE_APPEND -> checkFileChange(
                    payerId, txBody.fileAppendOrThrow().fileIDOrThrow().fileNum());
                // Authorization for crypto updates
            case CRYPTO_UPDATE -> checkCryptoUpdate(payerId, txBody.cryptoUpdateAccountOrThrow());

                // Authorization for deletes
            case FILE_DELETE -> checkEntityDelete(
                    txBody.fileDeleteOrThrow().fileIDOrThrow().fileNum());
            case CRYPTO_DELETE -> checkEntityDelete(
                    txBody.cryptoDeleteOrThrow().deleteAccountIDOrThrow().accountNumOrThrow());
            case NODE_CREATE, NODE_DELETE -> checkNodeChange(payerId);
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

    private boolean hasSoftwareUpdatePrivilege(@NonNull final AccountID accountID) {
        return accountID.accountNumOrThrow() == accountsConfig.softwareUpdateAdmin() || isSuperUser(accountID);
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

    private boolean hasNodeChangePrivilige(@NonNull final AccountID accountID) {
        return accountID.accountNumOrThrow() == accountsConfig.addressBookAdmin() || isSuperUser(accountID);
    }

    private SystemPrivilege checkFreeze(@NonNull final AccountID accountID) {
        return hasFreezePrivilege(accountID) ? AUTHORIZED : UNAUTHORIZED;
    }

    private SystemPrivilege checkSystemDelete(
            @NonNull final AccountID accountID, @NonNull final SystemDeleteTransactionBody op) {
        final var entityNum = op.hasFileID()
                ? op.fileIDOrThrow().fileNum()
                : op.contractIDOrThrow().contractNumOrThrow();
        if (isSystemEntity(entityNum)) {
            return IMPERMISSIBLE;
        }
        return hasSystemDeletePrivilege(accountID) ? AUTHORIZED : UNAUTHORIZED;
    }

    private SystemPrivilege checkSystemUndelete(
            @NonNull final AccountID accountID, @NonNull final SystemUndeleteTransactionBody op) {
        final var entityNum = op.hasFileID()
                ? op.fileIDOrThrow().fileNum()
                : op.contractIDOrThrow().contractNumOrThrow();
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
            return hasAddressBookPrivilege(accountID) || hasExchangeRatePrivilige(accountID)
                    ? AUTHORIZED
                    : UNAUTHORIZED;
        } else if (entityNum == filesConfig.feeSchedules()) {
            return hasFeeSchedulePrivilige(accountID) ? AUTHORIZED : UNAUTHORIZED;
        } else if (entityNum == filesConfig.exchangeRates()) {
            return hasExchangeRatePrivilige(accountID) ? AUTHORIZED : UNAUTHORIZED;
        } else if (filesConfig.softwareUpdateRange().left() <= entityNum
                && entityNum <= filesConfig.softwareUpdateRange().right()) {
            return hasSoftwareUpdatePrivilege(accountID) ? AUTHORIZED : UNAUTHORIZED;
        } else if (entityNum == filesConfig.throttleDefinitions()) {
            return hasAddressBookPrivilege(accountID) || hasExchangeRatePrivilige(accountID)
                    ? AUTHORIZED
                    : UNAUTHORIZED;
        }
        return UNAUTHORIZED;
    }

    private SystemPrivilege checkCryptoUpdate(
            @NonNull final AccountID payerId, @NonNull final CryptoUpdateTransactionBody op) {
        // while dispatching hollow account finalization transaction body, the accountId is set to DEFAULT
        final var targetId = op.accountIDToUpdateOrElse(AccountID.DEFAULT);
        final long targetNum = targetId.accountNumOrElse(0L);
        final var treasury = accountsConfig.treasury();
        final var payerNum = payerId.accountNumOrElse(0L);

        if (!isSystemEntity(targetNum)) {
            return UNNECESSARY;
        } else {
            if (payerNum == treasury) {
                return AUTHORIZED;
            } else if (payerNum == accountsConfig.systemAdmin()) {
                return isTreasury(targetId) ? UNAUTHORIZED : AUTHORIZED;
            } else {
                return isTreasury(targetId) ? UNAUTHORIZED : UNNECESSARY;
            }
        }
    }

    private SystemPrivilege checkNodeChange(@NonNull final AccountID payerId) {
        return hasNodeChangePrivilige(payerId) ? AUTHORIZED : UNAUTHORIZED;
    }

    private SystemPrivilege checkEntityDelete(final long entityNum) {
        return isSystemEntity(entityNum) ? IMPERMISSIBLE : UNNECESSARY;
    }
}
