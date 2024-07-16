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

package com.hedera.services.bdd.suites.token;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
@DisplayName("evmValidation")
@HapiTestLifecycle
public class UpdateTokenFeeScheduleSuite {

    static final String updateTokenFeeSchedules = "UpdateTokenFeeSchedules";
    static final AtomicReference<Address> fungibleTokenNum = new AtomicReference<>();
    static final AtomicReference<Address> account = new AtomicReference<>();
    static final AtomicReference<Address> collector = new AtomicReference<>();
    private static final KeyShape THRESHOLD_KEY_SHAPE = KeyShape.threshOf(1, ED25519, CONTRACT);

    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
        testLifecycle.doAdhoc(
                cryptoCreate("collector").balance(100 * ONE_HUNDRED_HBARS).exposingEvmAddressTo(collector::set),
                uploadInitCode(updateTokenFeeSchedules),
                contractCreate(updateTokenFeeSchedules),
                newKeyNamed("feeScheduleKey"),
                newKeyNamed("adminKey"),
                newKeyNamed("thresholdKey").shape(THRESHOLD_KEY_SHAPE.signedWith(sigs(ON, updateTokenFeeSchedules))),
                cryptoCreate("account")
                        .balance(100 * ONE_HUNDRED_HBARS)
                        .exposingEvmAddressTo(account::set)
                        .key("feeScheduleKey"),
                tokenCreate("fungibleToken")
                        .treasury("account")
                        .adminKey("adminKey")
                        .feeScheduleKey(updateTokenFeeSchedules)
                        .exposingAddressTo(fungibleTokenNum::set));
    }

    @HapiTest
    @DisplayName("try to set fixed hbar fee to a token")
    public Stream<DynamicTest> trySetFixedHbarFeeToToken() {
        return hapiTest(
                contractCall(
                                updateTokenFeeSchedules,
                                "updateFungibleFixedHbarFee",
                                fungibleTokenNum.get(),
                                10L,
                                collector.get())
                        .signingWith("thresholdKey")
                        .logged()
                        .hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
                        .via("setFixedHbarFeeToToken"),
                getTxnRecord("setFixedHbarFeeToToken").logged());
    }
}
