/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.support.validators;

import static com.hedera.node.app.hapi.utils.CommonUtils.extractTransactionBody;
import static java.util.Objects.requireNonNull;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.bdd.junit.support.RecordStreamValidator;
import com.hedera.services.bdd.junit.support.RecordWithSidecars;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

/**
 * This validator checks that transactions that contain ContractCreateTransactionBody
 * have an admin key set to self manage the contract.
 */
public class ContractSelfAdminKeyValidator implements RecordStreamValidator {
    private static final Logger log = LogManager.getLogger(ContractSelfAdminKeyValidator.class);
    private final long newContractNum;

    public ContractSelfAdminKeyValidator(final long newContractNum) {
        this.newContractNum = newContractNum;
    }

    @Override
    public void validateRecordsAndSidecars(@NonNull final List<RecordWithSidecars> recordsWithSidecars) {
        requireNonNull(recordsWithSidecars);
        for (final var recordWithSidecars : recordsWithSidecars) {
            final var items = recordWithSidecars.recordFile().getRecordStreamItemsList();
            for (final var item : items) {
                try {
                    final var txnBody = extractTransactionBody(item.getTransaction());
                    if (txnBody.hasContractCreateInstance()) {
                        final var adminKey = txnBody.getContractCreateInstance().getAdminKey();
                        if (adminKey.hasContractID() && adminKey.getContractID().getContractNum() != newContractNum) {
                            Assertions.fail("Contract create transaction does not have admin key set to self manage");
                        }
                    }
                } catch (InvalidProtocolBufferException e) {
                    log.error("Unable to parse and classify item {}", item, e);
                    throw new IllegalStateException(e);
                }
            }
        }
    }
}
