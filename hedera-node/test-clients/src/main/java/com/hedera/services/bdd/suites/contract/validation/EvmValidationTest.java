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

package com.hedera.services.bdd.suites.contract.validation;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_BYTECODE_EMPTY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.dsl.annotations.Contract;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
@DisplayName("evmValidation")
@HapiTestLifecycle
public class EvmValidationTest {
    @Nested
    @DisplayName("calling balanceOf")
    class BalanceOf {
        @Contract(contract = "BalanceChecker46Version", creationGas = 10_000_000L)
        static SpecContract balanceChecker46Version;

        @HapiTest
        @DisplayName("succeeds on non-existent contract")
        public Stream<DynamicTest> canCallBalanceOperationNonExtantContract() {
            final var INVALID_ADDRESS = "0x0000000000000000000000000000000000123456";
            return hapiTest(balanceChecker46Version
                    .call("balanceOf", asHeadlongAddress(INVALID_ADDRESS))
                    .andAssert(txn -> txn.hasKnownStatuses(SUCCESS)));
        }
    }

    @Nested
    @DisplayName("calling touchAccountContract")
    class TouchAccountContract {
        private static String touchAccountContract = "TouchAccountContract";

        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
            testLifecycle.doAdhoc(uploadInitCode(touchAccountContract), contractCreate(touchAccountContract));
        }

        @Nested
        @DisplayName("fails")
        class CallFails {
            @HapiTest
            @DisplayName("when transferring value to long zero address 00000000000000000000000000000000001117d0 ")
            public Stream<DynamicTest> lazyCreateToLongZeroFails() {
                final var LONG_ZERO_ADDRESS = "00000000000000000000000000000000001117d0";
                return callContractWithValue(LONG_ZERO_ADDRESS, CONTRACT_REVERT_EXECUTED);
            }

            @HapiTest
            @DisplayName("when transferring value to long zero burn address 000000000000000000000000000000000000dEaD ")
            public Stream<DynamicTest> lazyCreateToLongZeroBurnAddressFails() {
                final var LONG_ZERO_BURN_ADDRESS = "000000000000000000000000000000000000dEaD";
                return callContractWithValue(LONG_ZERO_BURN_ADDRESS, CONTRACT_REVERT_EXECUTED);
            }

            @HapiTest
            @DisplayName("when transferring value to all zero address 0000000000000000000000000000000000000000 ")
            public Stream<DynamicTest> lazyCreateToAllZeroFails() {
                final var ALL_ZERO_ADDRESS = "0000000000000000000000000000000000000000";
                return callContractWithValue(ALL_ZERO_ADDRESS, INVALID_CONTRACT_ID);
            }
        }

        @Nested
        @DisplayName("succeeds")
        class CallSucceeds {
            @HapiTest
            @DisplayName("when transferring value to evm address 0000000100000000000000020000000000000003")
            public Stream<DynamicTest> lazyCreateToEvmAddressSucceeds() {
                final var EVM_ADDRESS = "0000000100000000000000020000000000000003";
                return callContractWithValue(EVM_ADDRESS, ResponseCodeEnum.SUCCESS);
            }

            @HapiTest
            @DisplayName("when transferring value to realistic evm address 388C818CA8B9251b393131C08a736A67ccB19297")
            public Stream<DynamicTest> lazyCreateToRealisticEvmAddressSucceeds() {
                final var REALISTIC_EVM_ADDRESS = "388C818CA8B9251b393131C08a736A67ccB19297";
                return callContractWithValue(REALISTIC_EVM_ADDRESS, ResponseCodeEnum.SUCCESS);
            }
        }

        private static Stream<DynamicTest> callContractWithValue(
                final String address, final ResponseCodeEnum expectedStatus) {
            return hapiTest(contractCall(touchAccountContract, "touchAccount", asHeadlongAddress(address))
                    .gas(100_000L)
                    .sending(ONE_HBAR)
                    .hasKnownStatus(expectedStatus));
        }
    }

    @Nested
    @DisplayName("empty contract")
    class EmptyContract {
        private static String emptyContract = "EmptyContract";

        @HapiTest
        @DisplayName("should fail to deploy")
        public Stream<DynamicTest> canCallBalanceOperationNonExtantContract() {
            return hapiTest(
                    uploadInitCode(emptyContract),
                    contractCreate(emptyContract)
                            .inlineInitCode(ByteString.copyFrom(new byte[0]))
                            .hasKnownStatus(CONTRACT_BYTECODE_EMPTY));
        }
    }
}
