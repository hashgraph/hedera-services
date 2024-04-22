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
import com.hedera.node.app.service.mono.statedumpers.accounts.BBMHederaAccount;
import com.hedera.services.bdd.junit.RecordStreamValidator;
import com.hedera.services.bdd.junit.RecordWithSidecars;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.identityconnectors.common.CollectionUtil;

public class AccountAliasValidator implements RecordStreamValidator {

    @Override
    public void validateAccountAliases(Set<BBMHederaAccount> accountsFromState, List<RecordWithSidecars> records) {

        Set<ByteString> bytes = accountsFromState.stream().map(a -> a.alias()).filter(Objects::nonNull)
                .map(a -> ByteString.copyFrom(a.toByteArray()))
                .collect(Collectors.toSet());

        Set<ByteString> recordStreamBytes = new HashSet<>();
        for (final var recordWithSidecars : records) {
            final var items = recordWithSidecars.recordFile().getRecordStreamItemsList();
            for (final var item : items) {
                TransactionBody transactionBody = null;
                try {

                    var transaction = item.getTransaction();
                    if (!transaction.getSignedTransactionBytes().equals(ByteString.EMPTY)) {
                        var signedTransaction = SignedTransaction.parseFrom(transaction.getSignedTransactionBytes());
                        transactionBody = TransactionBody.parseFrom(signedTransaction.getBodyBytes());
                    } else if (!transaction.getBodyBytes().equals(ByteString.EMPTY)) {
                        transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
                    } else if (transaction.hasBody()) {
                        transactionBody = transaction.getBody();
                    }
                } catch (InvalidProtocolBufferException e) {
                    System.out.println("Error reading protobuff");
                }

                final var grpcRecord = item.getRecord();
                var alias =
                        grpcRecord.getAlias() != ByteString.EMPTY
                                ? grpcRecord.getAlias()
                                : transactionBody.getCryptoCreateAccount().getAlias();

                recordStreamBytes.add(alias);
            }
        }

        if (CollectionUtil.isEmpty(bytes)) {
            System.out.println("Skip account alias validator since no accounts were read from state");
        }

        // do validations
        for (ByteString byteString : bytes) {
            if (!recordStreamBytes.contains(byteString)) {
                // if we get in here it's not good
                System.out.println("Missing alias in recordStream");
            }
        }
    }
}
