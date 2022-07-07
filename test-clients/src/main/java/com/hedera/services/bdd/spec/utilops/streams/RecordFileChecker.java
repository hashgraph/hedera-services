package com.hedera.services.bdd.spec.utilops.streams;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */


import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.queries.file.HapiGetFileContents;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hedera.services.bdd.spec.verification.NodeSignatureVerifier;
import com.hedera.services.recordstreaming.RecordStreamingUtils;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;

public class RecordFileChecker extends UtilOp {
    private static final Logger log = LogManager.getLogger(RecordFileChecker.class);

    private static final String PATH_TO_LOCAL_STREAMS = "../hedera-node/data/recordstreams/record%d.%d.%d";
    private static final String ERROR_MESSAGE = "Record file %s could not be read in node %d.%d.%d stream files.";
    private static final String TRANSACTION_RECORD_ERROR_MESSAGE = "Transaction record validation from file failed. ";
    private static final String TRANSACTION_ERROR_MESSAGE = "Transaction validation from file failed";
    private static final String SIGNATURE_FILE_MISSING_ERROR = "Missing signature file.";


    private final String recordFileName;
    private final List<TransactionRecord> transactionRecord;
    private final List<Transaction> transactions;

    public RecordFileChecker(String recordFileName, List<Transaction> transactions, TransactionRecord... transactionRecord) {
        this.recordFileName = recordFileName;
        this.transactionRecord = Arrays.asList(transactionRecord);
        this.transactions = transactions;
    }

    @Override
    protected boolean submitOp(HapiApiSpec spec) throws Throwable {
        lookForFile(spec);
        return false;
    }

    private void lookForFile(HapiApiSpec spec) throws Exception {
        var addressBook = downloadBook(spec);
        NodeSignatureVerifier verifier = new NodeSignatureVerifier(addressBook);

        for (var address : addressBook.getNodeAddressList()) {
            var nodeAccountId = address.getNodeAccountId();
            var pathToFile = Path.of(
                    String.format(PATH_TO_LOCAL_STREAMS,
                            nodeAccountId.getShardNum(),
                            nodeAccountId.getRealmNum(),
                            nodeAccountId.getAccountNum()
                    ), recordFileName);

            var pathToSig = Path.of(
                    String.format(PATH_TO_LOCAL_STREAMS,
                            nodeAccountId.getShardNum(),
                            nodeAccountId.getRealmNum(),
                            nodeAccountId.getAccountNum()
                    ), recordFileName + "_sig");


            final var signatureFilePair = RecordStreamingUtils.readSignatureFile(pathToSig.toString());
            Assertions.assertTrue(signatureFilePair.getRight().isPresent(), SIGNATURE_FILE_MISSING_ERROR);

            final var recordFileVersionAndProto = RecordStreamingUtils.readRecordStreamFile(pathToFile.toString());
            var recordStreamFileOptional = recordFileVersionAndProto.getRight();

            Assertions.assertTrue(
                    recordStreamFileOptional.isPresent(),
                    String.format(
                            ERROR_MESSAGE,
                            pathToFile,
                            nodeAccountId.getShardNum(),
                            nodeAccountId.getRealmNum(),
                            nodeAccountId.getAccountNum()
                    )
            );

            final var recordStreamFile = recordStreamFileOptional.get();
            final var actualRecordsInFile = recordStreamFile
                    .getRecordStreamItemsList()
                    .stream()
                    .map(RSI -> Pair.of(RSI.getTransaction(), RSI.getRecord()))
                    .toList();

            for (int i = 0; i < actualRecordsInFile.size(); i++) {
                Assertions.assertEquals(actualRecordsInFile.get(i).getRight(), transactionRecord.get(i), TRANSACTION_RECORD_ERROR_MESSAGE);
                Assertions.assertEquals(actualRecordsInFile.get(i).getLeft(), transactions.get(i), TRANSACTION_ERROR_MESSAGE);
            }

        }

    }

    private NodeAddressBook downloadBook(HapiApiSpec spec) throws Exception {
        String addressBook = spec.setup().nodeDetailsName();
        HapiGetFileContents op = getFileContents(addressBook);
        allRunFor(spec, op);
        byte[] serializedBook = op.getResponse().getFileGetContents().getFileContents().getContents().toByteArray();
        return NodeAddressBook.parseFrom(serializedBook);
    }
}
