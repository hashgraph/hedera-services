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

package com.hedera.services.bdd.spec.utilops.domain;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;

/**
 * A record providing convenient access to just a {@link TransactionBody} and a {@link TransactionRecord}
 * from a record stream item for use in snapshot fuzzy-matching.
 *
 * @param itemBody the transaction body
 * @param itemRecord the transaction record
 */
public record ParsedItem(TransactionBody itemBody, TransactionRecord itemRecord) {
    private static final FileID PROPERTIES_FILE_ID =
            FileID.newBuilder().setFileNum(121).build();
    private static final FileID FEE_SCHEDULE_FILE_ID =
            FileID.newBuilder().setFileNum(111).build();

    public ResponseCodeEnum status() {
        return itemRecord.getReceipt().getStatus();
    }

    public static ParsedItem parse(final RecordStreamItem item) throws InvalidProtocolBufferException {
        final var txn = item.getTransaction();
        final TransactionBody body;
        if (txn.getBodyBytes().size() > 0) {
            body = TransactionBody.parseFrom(txn.getBodyBytes());
        } else {
            final var signedTxnBytes = item.getTransaction().getSignedTransactionBytes();
            final var signedTxn = SignedTransaction.parseFrom(signedTxnBytes);
            body = TransactionBody.parseFrom(signedTxn.getBodyBytes());
        }
        return new ParsedItem(body, item.getRecord());
    }

    public boolean isSpecialFileChange() {
        var fileID = FileID.newBuilder().build();
        final boolean isFileUpdate = itemBody.hasFileUpdate();
        if (isFileUpdate) {
            fileID = itemBody.getFileUpdate().getFileID();
        }

        final boolean isFileAppend = itemBody.hasFileAppend();
        if (isFileAppend) {
            fileID = itemBody.getFileAppend().getFileID();
        }

        final var status = itemRecord.getReceipt().getStatus();
        return (status == SUCCESS || status == FEE_SCHEDULE_FILE_PART_UPLOADED)
                && (isFileUpdate || isFileAppend)
                && (PROPERTIES_FILE_ID.equals(fileID) || FEE_SCHEDULE_FILE_ID.equals(fileID));
    }
}
