///*
// * Copyright (C) 2024 Hedera Hashgraph, LLC
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package com.hedera.node.app.workflows.handle.dispatch;
//
//import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
//import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.PRECEDING;
//import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.SCHEDULED;
//import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.USER;
//import static com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer.NOOP_EXTERNALIZED_RECORD_CUSTOMIZER;
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertNotNull;
//import static org.junit.jupiter.api.Assertions.assertThrows;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
//import com.hedera.hapi.node.base.AccountID;
//import com.hedera.hapi.node.base.HederaFunctionality;
//import com.hedera.hapi.node.base.NftTransfer;
//import com.hedera.hapi.node.base.SignatureMap;
//import com.hedera.hapi.node.base.Timestamp;
//import com.hedera.hapi.node.base.TokenID;
//import com.hedera.hapi.node.base.TokenTransferList;
//import com.hedera.hapi.node.base.Transaction;
//import com.hedera.hapi.node.base.TransactionID;
//import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
//import com.hedera.hapi.node.transaction.TransactionBody;
//import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
//import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
//import com.hedera.node.app.workflows.TransactionInfo;
//import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
//import com.hedera.node.app.workflows.handle.stack.AbstractSavePoint;
//import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
//import com.hedera.pbj.runtime.io.buffer.Bytes;
//import com.swirlds.config.api.Configuration;
//import java.time.Instant;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//@ExtendWith(MockitoExtension.class)
//public class ChildRecordBuilderFactoryTest {
//    static final Instant consensusTime = Instant.ofEpochSecond(1_234_567L);
//
//    private ChildRecordBuilderFactory factory;
//    private ExternalizedRecordCustomizer customizer;
//
//    private static final AccountID payerId =
//            AccountID.newBuilder().accountNum(1_234L).build();
//    private static final CryptoTransferTransactionBody transferBody = CryptoTransferTransactionBody.newBuilder()
//            .tokenTransfers(TokenTransferList.newBuilder()
//                    .token(TokenID.DEFAULT)
//                    .nftTransfers(NftTransfer.newBuilder()
//                            .receiverAccountID(AccountID.DEFAULT)
//                            .senderAccountID(AccountID.DEFAULT)
//                            .serialNumber(1)
//                            .build())
//                    .build())
//            .build();
//    private static final TransactionBody txBody = asTxn(transferBody, payerId, consensusTime);
//    private static final TransactionInfo txnInfo = new TransactionInfo(
//            Transaction.newBuilder().body(txBody).build(),
//            txBody,
//            SignatureMap.DEFAULT,
//            Bytes.EMPTY,
//            HederaFunctionality.CRYPTO_TRANSFER);
//    @Mock
//    private AbstractSavePoint parent;
//
//    @BeforeEach
//    void setUp() {
//        configuration = HederaTestConfigBuilder.createConfig();
//        customizer = NOOP_EXTERNALIZED_RECORD_CUSTOMIZER;
//        factory = new ChildRecordBuilderFactory();
//    }
//
//    @Test
//    void testRecordBuilderForPrecedingRemovable() {
//        var recordBuilder = factory.recordBuilderFor(
//                txnInfo,
//                PRECEDING,
//                SingleTransactionRecordBuilder.ReversingBehavior.REMOVABLE,
//                customizer,
//                parent
//                );
//
//        assertNotNull(recordBuilder);
//        assertTrue(recordListBuilder.precedingRecordBuilders().contains(recordBuilder));
//    }
//
//    @Test
//    void testRecordBuilderForPrecedingReversible() {
//        var recordBuilder = factory.recordBuilderFor(
//                txnInfo,
//                PRECEDING,
//                SingleTransactionRecordBuilder.ReversingBehavior.REVERSIBLE,
//                null,
//                parent);
//
//        assertNotNull(recordBuilder);
//        assertTrue(recordListBuilder.precedingRecordBuilders().contains(recordBuilder));
//    }
//
//    @Test
//    void testRecordBuilderForChildRemovable() {
//        var recordBuilder = factory.recordBuilderFor(
//                txnInfo,
//                CHILD,
//                SingleTransactionRecordBuilder.ReversingBehavior.REMOVABLE,
//                customizer,
//                parent);
//
//        assertNotNull(recordBuilder);
//        assertTrue(recordListBuilder.childRecordBuilders().contains(recordBuilder));
//    }
//
//    @Test
//    void testRecordBuilderForChildReversible() {
//        var recordBuilder = factory.recordBuilderFor(
//                txnInfo,
//                CHILD,
//                SingleTransactionRecordBuilder.ReversingBehavior.REVERSIBLE,
//                customizer,
//                parent);
//
//        assertNotNull(recordBuilder);
//        assertTrue(recordListBuilder.childRecordBuilders().contains(recordBuilder));
//    }
//
//    @Test
//    void testRecordBuilderForUnsupportedReversingBehavior() {
//        assertThrows(
//                IllegalArgumentException.class,
//                () -> factory.recordBuilderFor(
//                        txnInfo,
//                        CHILD,
//                        SingleTransactionRecordBuilder.ReversingBehavior.IRREVERSIBLE,
//                        customizer,
//                        parent));
//    }
//
//    @Test
//    void testRecordBuilderForScheduled() {
//        var recordBuilder = factory.recordBuilderFor(
//                txnInfo,
//                SCHEDULED,
//                SingleTransactionRecordBuilder.ReversingBehavior.REVERSIBLE,
//                customizer,
//                parent);
//
//        assertNotNull(recordBuilder);
//        assertTrue(recordListBuilder.childRecordBuilders().contains(recordBuilder));
//    }
//
//    @Test
//    void testRecordBuilderForUser() {
//        assertThrows(
//                IllegalArgumentException.class,
//                () -> factory.recordBuilderFor(
//                        txnInfo,
//                        USER,
//                        SingleTransactionRecordBuilder.ReversingBehavior.IRREVERSIBLE,
//                        customizer,
//                        parent));
//    }
//
//    @Test
//    void testInitializeUserRecord() {
//        var recordBuilder = factory.recordBuilderFor(
//                txnInfo,
//                CHILD,
//                SingleTransactionRecordBuilder.ReversingBehavior.REMOVABLE,
//                customizer,
//                parent);
//
//        assertNotNull(recordBuilder);
//        assertEquals(txnInfo.transaction(), recordBuilder.transaction());
//        assertEquals(txnInfo.txBody().transactionID(), recordBuilder.transactionID());
//        assertEquals(
//                txnInfo.txBody().memo(),
//                recordBuilder.build().transaction().body().memo());
//        assertEquals(txnInfo.signedBytes(), recordBuilder.build().transaction().signedTransactionBytes());
//    }
//
//    public static TransactionBody asTxn(
//            final CryptoTransferTransactionBody body, final AccountID payerId, Instant consensusTimestamp) {
//        return TransactionBody.newBuilder()
//                .transactionID(TransactionID.newBuilder()
//                        .accountID(payerId)
//                        .transactionValidStart(Timestamp.newBuilder()
//                                .seconds(consensusTimestamp.getEpochSecond())
//                                .build())
//                        .build())
//                .memo("test memo")
//                .cryptoTransfer(body)
//                .build();
//    }
//}
