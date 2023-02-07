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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.ArrayList;
import java.util.List;

/** This validator validates expiry contract records at the end of the CI test run: */
public class ExpiryRecordsValidator implements RecordStreamValidator {
    private static final String AUTO_RENEWAL_MEMO = " was automatically renewed";
    private static final String AUTO_EXPIRY_MEMO = " was automatically deleted";

    @Override
    @SuppressWarnings("java:S106")
    public void validate(final List<RecordWithSidecars> recordsWithSidecars) {
        final var renewalRecords = new ArrayList<TransactionRecord>();
        final var expiredRecords = new ArrayList<TransactionRecord>();
        getExpiryRecordsFrom(recordsWithSidecars, renewalRecords, expiredRecords);
        System.out.println("Expired records: " + expiredRecords);
        System.out.println("Renewal records: " + renewalRecords);

        validateRenewalRecords(renewalRecords);
        validateExpiredRecords(expiredRecords);
    }

    private void validateExpiredRecords(List<TransactionRecord> expiredRecords) {
        for (final var item : expiredRecords) {
            assertTrue(item.getReceipt().getStatus().equals(SUCCESS));

            final var expiredNum = getEntityNumFromAutoExpiryMemo(item.getMemo());
            ;

            for (final var transfers : item.getTransferList().getAccountAmountsList()) {
                if (transfers.getAccountID().getAccountNum() == expiredNum) {
                    assertTrue(
                            transfers.getAmount() < 0,
                            "Transfer amount in transfer list "
                                    + "from expired entity should be negative");
                } else {
                    assertTrue(
                            transfers.getAmount() > 0,
                            "Transfer amount in transfer list to other"
                                    + " entities should be positive");
                }
            }

            for (final var list : item.getTokenTransferListsList()) {
                for (final var transfer : list.getTransfersList()) {
                    if (transfer.getAccountID().getAccountNum() == expiredNum) {
                        assertTrue(
                                transfer.getAmount() < 0,
                                "Transfer amount in token "
                                        + "transfer list from expired entity should be negative");
                    } else {
                        assertTrue(
                                transfer.getAmount() > 0,
                                "Transfer amount in token "
                                        + "transfer list to other entities should be positive");
                    }
                }
                for (final var transfer : list.getNftTransfersList()) {
                    assertEquals(expiredNum, transfer.getSenderAccountID().getAccountNum());
                    assertTrue(transfer.getReceiverAccountID().getAccountNum() > 1000);
                    assertTrue(transfer.getSerialNumber() > 0);
                }
            }
        }
    }

    private void validateRenewalRecords(List<TransactionRecord> renewalRecords) {
        for (final var item : renewalRecords) {
            assertTrue(item.getReceipt().getStatus().equals(SUCCESS));

            final var renewedNum = getEntityNumFromAutoRenewalMemo(item.getMemo());

            for (final var transfers : item.getTransferList().getAccountAmountsList()) {
                if (transfers.getAccountID().getAccountNum() == renewedNum) {
                    assertTrue(transfers.getAmount() < 0);
                }
                if (transfers.getAccountID().getAccountNum() == 98) {
                    assertTrue(transfers.getAmount() > 0);
                }
                if (transfers.getAccountID().getAccountNum() == 800) {
                    assertTrue(transfers.getAmount() > 0);
                }
                if (transfers.getAccountID().getAccountNum() == 801) {
                    assertTrue(transfers.getAmount() > 0);
                }
            }
            assertTrue(item.getTokenTransferListsList().isEmpty());
        }
    }

    private Long getEntityNumFromAutoRenewalMemo(final String memo) {
        final var entity =
                memo.substring(memo.indexOf("0.0."), memo.indexOf(AUTO_RENEWAL_MEMO)).substring(4);
        return Long.valueOf(entity);
    }

    private Long getEntityNumFromAutoExpiryMemo(final String memo) {
        final var entity =
                memo.substring(memo.indexOf("0.0."), memo.indexOf(AUTO_EXPIRY_MEMO)).substring(4);
        return Long.valueOf(entity);
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
