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

package com.hedera.services.bdd.suites.contract.precompile.token;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;

@Tag(SMART_CONTRACT)
@DisplayName("transferToken")
@SuppressWarnings("java:S1192")
@HapiTestLifecycle
@TestMethodOrder(OrderAnnotation.class)
public class TransferTokenTest {

    private static final String TOKEN_TRANSFER_CONTRACT = "TokenTransferContract";
    private static final String TOKEN_RECEIVER_CONTRACT = "NestedHTSTransferrer";
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String ACCOUNT = "account";
    private static final AtomicReference<Address> fungibleTokenNum = new AtomicReference<>();
    private static final AtomicReference<Long> transferContractId = new AtomicReference<>();
    private static final AtomicReference<Long> receiverContractId = new AtomicReference<>();

    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
        testLifecycle.doAdhoc(
                // Token owner contract that trys to transfer owned tokens
                uploadInitCode(TOKEN_TRANSFER_CONTRACT),
                contractCreate(TOKEN_TRANSFER_CONTRACT).gas(1_000_000L).exposingNumTo(transferContractId::set),

                // Contract that is the recipient of the tokens
                uploadInitCode(TOKEN_RECEIVER_CONTRACT),
                contractCreate(TOKEN_RECEIVER_CONTRACT).gas(1_000_000L).exposingNumTo(receiverContractId::set),

                // Create the treasury account
                cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),

                // Create fungible token
                tokenCreate(FUNGIBLE_TOKEN)
                        .treasury(ACCOUNT)
                        .initialSupply(100)
                        .exposingAddressTo(fungibleTokenNum::set),

                // Do associations
                tokenAssociate(TOKEN_TRANSFER_CONTRACT, FUNGIBLE_TOKEN),
                tokenAssociate(TOKEN_RECEIVER_CONTRACT, FUNGIBLE_TOKEN),

                // Transfer fungible tokens to the TokenTransferContract contract
                cryptoTransfer(moving(20, FUNGIBLE_TOKEN).between(ACCOUNT, TOKEN_TRANSFER_CONTRACT)));
    }

    @Nested
    @DisplayName("successful when")
    @Order(1)
    class SuccessfulTransferTokenTest {
        @HapiTest
        @DisplayName("transferring owner's tokens using transferToken function")
        public Stream<DynamicTest> transferUsingTransferToken() {
            return hapiTest(contractCall(
                            TOKEN_TRANSFER_CONTRACT,
                            "transferTokenPublic",
                            fungibleTokenNum.get(),
                            asHeadlongAddress(Bytes.ofUnsignedLong(transferContractId.get())
                                    .toArray()),
                            asHeadlongAddress(Bytes.ofUnsignedLong(receiverContractId.get())
                                    .toArray()),
                            2L)
                    .gas(1_000_000L));
        }

        @HapiTest
        @DisplayName("transferring owner's tokens using transferFrom function given allowance")
        public Stream<DynamicTest> transferUsingTransferFromWithAllowance() {
            return hapiTest(
                    contractCall(
                                    TOKEN_TRANSFER_CONTRACT,
                                    "approvePublic",
                                    fungibleTokenNum.get(),
                                    asHeadlongAddress(Bytes.ofUnsignedLong(transferContractId.get())
                                            .toArray()),
                                    BigInteger.valueOf(2L))
                            .gas(1_000_000L),
                    contractCall(
                                    TOKEN_TRANSFER_CONTRACT,
                                    "transferFromPublic",
                                    fungibleTokenNum.get(),
                                    asHeadlongAddress(Bytes.ofUnsignedLong(transferContractId.get())
                                            .toArray()),
                                    asHeadlongAddress(Bytes.ofUnsignedLong(receiverContractId.get())
                                            .toArray()),
                                    BigInteger.valueOf(2L))
                            .gas(1_000_000L));
        }
    }

    @Nested
    @DisplayName("fails when")
    @Order(2)
    class FailedTransferTokenTest {
        @HapiTest
        @DisplayName("transferring owner's tokens using transferFrom function without allowance")
        public Stream<DynamicTest> transferUsingTransferFromWithoutAllowance() {
            return hapiTest(contractCall(
                            TOKEN_TRANSFER_CONTRACT,
                            "transferFromPublic",
                            fungibleTokenNum.get(),
                            asHeadlongAddress(Bytes.ofUnsignedLong(transferContractId.get())
                                    .toArray()),
                            asHeadlongAddress(Bytes.ofUnsignedLong(receiverContractId.get())
                                    .toArray()),
                            BigInteger.valueOf(2L))
                    .gas(1_000_000L)
                    .hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED));
        }

        @HapiTest
        @DisplayName("transferring owner's tokens using transferToken function from receiver contract")
        public Stream<DynamicTest> transferUsingTransferFromReceiver() {
            return hapiTest(contractCall(
                            TOKEN_RECEIVER_CONTRACT,
                            "transfer",
                            fungibleTokenNum.get(),
                            asHeadlongAddress(Bytes.ofUnsignedLong(transferContractId.get())
                                    .toArray()),
                            asHeadlongAddress(Bytes.ofUnsignedLong(receiverContractId.get())
                                    .toArray()),
                            2L)
                    .gas(1_000_000L)
                    .hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED));
        }
    }
}
