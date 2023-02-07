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
package com.hedera.services.bdd.junit;

import com.hederahashgraph.api.proto.java.TransactionRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * This validator validates expiry contract records at the end of the CI test run:
 */
public class ExpiryRecordsValidator implements RecordStreamValidator {
    @Override
    @SuppressWarnings("java:S106")
    public void validate(final List<RecordWithSidecars> recordsWithSidecars) {
        final var renewalRecords = new ArrayList<TransactionRecord>();
        final var expiredRecords = new ArrayList<TransactionRecord>();
        getExpiryRecordsFrom(recordsWithSidecars, renewalRecords, expiredRecords);
        System.out.println("Expected expired records: " + expiredRecords);
        System.out.println("Expected renewal records: " + renewalRecords);
    }

    private void getExpiryRecordsFrom(
            final List<RecordWithSidecars> recordsWithSidecars,
            final List<TransactionRecord> renewalRecords,
            final List<TransactionRecord> expiredRecords) {
        for (final var recordWithSidecars : recordsWithSidecars) {
            final var items = recordWithSidecars.recordFile().getRecordStreamItemsList();
            items.stream()
                    .filter(
                            item ->
                                    item.getRecord()
                                            .getMemo()
                                            .contains("was automatically renewed"))
                    .forEach(item -> renewalRecords.add(item.getRecord()));

            items.stream()
                    .filter(
                            item ->
                                    item.getRecord()
                                            .getMemo()
                                            .contains("was automatically deleted"))
                    .forEach(item -> expiredRecords.add(item.getRecord()));
        }
    }
}
