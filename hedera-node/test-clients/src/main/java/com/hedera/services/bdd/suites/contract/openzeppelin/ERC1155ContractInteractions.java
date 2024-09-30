/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.contract.openzeppelin;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.EthSigsUtils;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
public class ERC1155ContractInteractions {
    private static final String ACCOUNT1 = "acc1";
    private static final String ACCOUNT2 = "acc2";
    private static final String CONTRACT = "GameItems";

    @HapiTest
    final Stream<DynamicTest> erc1155() {
        return hapiTest(
                newKeyNamed("ec").shape(SECP_256K1_SHAPE),
                cryptoCreate(ACCOUNT1),
                withOpContext((spec, log) -> allRunFor(
                        spec,
                        cryptoCreate(ACCOUNT2)
                                .balance(ONE_HUNDRED_HBARS)
                                .key("ec")
                                .alias(ByteString.copyFrom(EthSigsUtils.recoverAddressFromPubKey(spec.registry()
                                        .getKey("ec")
                                        .getECDSASecp256K1()
                                        .toByteArray()))))),
                uploadInitCode(CONTRACT),
                contractCreate(CONTRACT).gas(500_000L).via("contractCreate").payingWith(ACCOUNT2),
                getTxnRecord("contractCreate"),
                getAccountBalance(ACCOUNT2),
                getAccountInfo(ACCOUNT1).savingSnapshot(ACCOUNT1 + "Info"),
                getAccountInfo(ACCOUNT2).savingSnapshot(ACCOUNT2 + "Info"),
                withOpContext((spec, log) -> {
                    final var accountOneAddress =
                            spec.registry().getAccountInfo(ACCOUNT1 + "Info").getContractAccountID();
                    final var senderAddress =
                            spec.registry().getAccountInfo(ACCOUNT2 + "Info").getContractAccountID();

                    final var ops = new ArrayList<SpecOperation>();

                    /* approve for other accounts */
                    final var approveCall = contractCall(
                                    CONTRACT, "setApprovalForAll", asHeadlongAddress(accountOneAddress), true)
                            .via("acc1ApproveCall")
                            .payingWith(ACCOUNT2)
                            .signingWith(ACCOUNT2)
                            .hasKnownStatus(ResponseCodeEnum.SUCCESS);
                    ops.add(approveCall);

                    /* mint to the contract owner */
                    final var mintCall = contractCall(
                                    CONTRACT,
                                    "mintToken",
                                    BigInteger.ZERO,
                                    BigInteger.valueOf(10),
                                    asHeadlongAddress(senderAddress))
                            .via("contractMintCall")
                            .payingWith(ACCOUNT2)
                            .signingWith(ACCOUNT2)
                            .hasKnownStatus(ResponseCodeEnum.SUCCESS);
                    ops.add(mintCall);

                    /* transfer from - account to account */
                    final var transferCall = contractCall(
                                    CONTRACT,
                                    "safeTransferFrom",
                                    asHeadlongAddress(senderAddress),
                                    asHeadlongAddress(accountOneAddress),
                                    BigInteger.ZERO, // token id
                                    BigInteger.ONE, // amount
                                    "0x0".getBytes())
                            .via("contractTransferFromCall")
                            .payingWith(ACCOUNT2)
                            .signingWith(ACCOUNT2)
                            .hasKnownStatus(ResponseCodeEnum.SUCCESS);
                    ops.add(transferCall);
                    allRunFor(spec, ops);
                }),
                getTxnRecord("contractMintCall"),
                getTxnRecord("acc1ApproveCall"),
                getTxnRecord("contractTransferFromCall"));
    }
}
