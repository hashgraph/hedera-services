/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.validators;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.service.mono.statedumpers.accounts.BBMHederaAccount;
import com.hedera.services.bdd.junit.RecordStreamValidator;
import com.hedera.services.bdd.junit.RecordWithSidecars;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Validates the presence of account aliases in the record stream.
 * <p>
 * This validator ensures that account aliases retrieved from the state are present
 * in the record stream. It compares aliases stored in the state with those found in
 * the record stream and throws an exception if any aliases are missing.
 * </p>
 * <p>
 * The validation process involves parsing the transaction body from record stream items,
 * extracting aliases, and comparing them with the aliases stored in the state.
 * </p>
 * <p>
 * The validator logs warnings for missing aliases and throws exceptions if discrepancies
 * are found between the state and the record stream.
 * </p>
 */
public class AccountAliasValidator implements RecordStreamValidator {
    static final Logger log = LogManager.getLogger(AccountAliasValidator.class);

    /**
     * Checks if account aliases from state are present in the recordStream
     *
     * @param accountsFromState The set of accounts read from state.
     * @param records           The list of record stream items.
     */
    @Override
    public void validateAccountAliases(Set<BBMHederaAccount> accountsFromState, List<RecordWithSidecars> records) {
        if (records.isEmpty()) {
            log.info("RecordStream is empty");
            throw new IllegalArgumentException("RecordStream is empty");
        }

        if (accountsFromState.isEmpty()) {
            log.info("No accounts were read from state skipping account alias validation");
            return;
        }

        Set<ByteString> stateAliases = getStateAliases(accountsFromState);
        Map<ByteString, AccountID> aliasToAccount = getAliasToAccountMap(accountsFromState);

        Set<ByteString> recordStreamBytes = getRecordStreamAliases(records);

        log.info("Starting account alias validation");
        validateAliases(stateAliases, recordStreamBytes, aliasToAccount);
    }

    /**
     * Parses the transaction body from the given RecordStreamItem.
     *
     * @param recordStreamItem The RecordStreamItem from which to parse the transaction body.
     * @return The parsed TransactionBody, or null if parsing fails.
     */
    private TransactionBody parseTransaction(RecordStreamItem recordStreamItem) {
        try {
            var transaction = recordStreamItem.getTransaction();
            if (!transaction.getSignedTransactionBytes().equals(ByteString.EMPTY)) {
                var signedTransaction = SignedTransaction.parseFrom(transaction.getSignedTransactionBytes());
                return TransactionBody.parseFrom(signedTransaction.getBodyBytes());
            } else if (!transaction.getBodyBytes().equals(ByteString.EMPTY)) {
                return TransactionBody.parseFrom(transaction.getBodyBytes());
            } else if (transaction.hasBody()) {
                return transaction.getBody();
            }
        } catch (InvalidProtocolBufferException e) {
            log.warn("Error reading protobuff");
        }
        return null;
    }

    private Set<ByteString> getStateAliases(Set<BBMHederaAccount> accountsFromState) {
        return accountsFromState.stream()
                .map(BBMHederaAccount::alias)
                .map(a -> ByteString.copyFrom(a.toByteArray()))
                .collect(Collectors.toSet());
    }

    private Map<ByteString, AccountID> getAliasToAccountMap(Set<BBMHederaAccount> accountsFromState) {
        return accountsFromState.stream()
                .collect(Collectors.toMap(
                        a -> ByteString.copyFrom(a.alias().toByteArray()),
                        BBMHederaAccount::accountId,
                        (existingValue, newValue) -> existingValue));
    }

    /**
     * Extracts aliases from the record stream items.
     *
     * @param records The list of record stream items.
     * @return A set of aliases extracted from the record stream items.
     */
    private Set<ByteString> getRecordStreamAliases(List<RecordWithSidecars> records) {
        Set<ByteString> recordStreamBytes = new HashSet<>();
        for (final var recordWithSidecars : records) {
            final var items = recordWithSidecars.recordFile().getRecordStreamItemsList();
            for (final var item : items) {
                TransactionBody transactionBody = parseTransaction(item);
                if (transactionBody != null) {
                    ByteString alias = item.getRecord().getAlias();
                    if (alias.equals(ByteString.EMPTY)) {
                        alias = transactionBody.getCryptoCreateAccount().getAlias();
                    }
                    recordStreamBytes.add(alias);
                }

                if (item.getRecord().hasContractCreateResult()) {
                    final var evmAddress = item.getRecord().getContractCreateResult().getEvmAddress();
                    recordStreamBytes.add(evmAddress.toByteString().substring(2));
                }

                final var evmAddress = item.getRecord().getEvmAddress();
                if (!evmAddress.equals(ByteString.EMPTY)) {
                    recordStreamBytes.add(evmAddress);
                }
            }
        }
        return recordStreamBytes;
    }

    private void validateAliases(
            Set<ByteString> stateAliases,
            Set<ByteString> recordStreamBytes,
            Map<ByteString, AccountID> aliasToAccount) {
        for (ByteString byteString : stateAliases) {
            if (!recordStreamBytes.contains(byteString)) {
                log.warn("Missing alias in recordStream");
                AccountID accountID = aliasToAccount.get(byteString);
                if (accountID != null) {
                    throw new IllegalArgumentException(String.format(
                            "Alias for account %s from state not found in recordStream",
                            aliasToAccount.get(byteString).accountNum()));
                } else {
                    throw new IllegalArgumentException("Alias from state not found in recordStream");
                }
            }
        }
    }
}
