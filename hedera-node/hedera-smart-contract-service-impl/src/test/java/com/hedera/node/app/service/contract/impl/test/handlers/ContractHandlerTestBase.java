/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.contract.impl.test.handlers;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ContractHandlerTestBase {
//    protected static final String ACCOUNTS = "ACCOUNTS";
//    protected static final String ALIASES = "ALIASES";
//    protected final AccountID payer = asAccount("0.0.3");
//    protected final AccountID autoRenewAccountId = asAccount("0.0.10001");
//    protected final HederaKey payerKey = asHederaKey(A_COMPLEX_KEY).get();
//    protected final Key adminKey = A_COMPLEX_KEY;
//    protected final Key adminContractKey =
//            Key.newBuilder().setContractID(asContract("0.0.10002")).build();
//    protected final HederaKey adminHederaKey = asHederaKey(A_COMPLEX_KEY).get();
//    protected final HederaKey autoRenewHederaKey = asHederaKey(A_COMPLEX_KEY).get();
//    protected final Timestamp consensusTimestamp =
//            Timestamp.newBuilder().setSeconds(1_234_567L).build();
//    protected final ContractID targetContract =
//            ContractID.newBuilder().setContractNum(9_999L).build();
//
//    @Mock protected MerkleAccount payerAccount;
//    @Mock protected AccountKeyLookup keyLookup;
//
//    @BeforeEach
//    void commonSetUp() {
//        setUpPayer();
//    }
//
//    protected void basicMetaAssertions(
//            final TransactionMetadata meta,
//            final int nonPayerKeySize,
//            final boolean failed,
//            final ResponseCodeEnum failureStatus) {
//        assertEquals(nonPayerKeySize, meta.requiredNonPayerKeys().size());
//        assertTrue(failed ? meta.failed() : !meta.failed());
//        assertEquals(failureStatus, meta.status());
//    }
//
//    protected void setUpPayer() {
//        lenient()
//                .when(keyLookup.getKey(payer))
//                .thenReturn(KeyOrLookupFailureReason.withKey(payerKey));
//        lenient().when(payerAccount.getAccountKey()).thenReturn((JKey) payerKey);
//    }
}
