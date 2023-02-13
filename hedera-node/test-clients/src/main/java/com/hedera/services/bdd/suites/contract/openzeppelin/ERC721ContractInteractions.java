/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import java.math.BigInteger;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ERC721ContractInteractions extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ERC721ContractInteractions.class);

    public static void main(String... args) {
        new ERC721ContractInteractions().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(callsERC721ContractInteractions());
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    private HapiSpec callsERC721ContractInteractions() {
        final var CONTRACT = "GameItem";
        final var nftId = BigInteger.ONE;
        final var CREATE_TX = "create";
        final var MINT_TX = "mint";
        final var APPROVE_TX = "approve";
        final var TRANSFER_FROM_TX = "transferFrom";

        return defaultHapiSpec("CallsERC721ContractInteractions")
                .given(
                        QueryVerbs.getAccountBalance(DEFAULT_CONTRACT_SENDER),
                        uploadInitCode(CONTRACT))
                .when(
                        QueryVerbs.getAccountBalance(DEFAULT_CONTRACT_SENDER),
                        contractCreate(CONTRACT)
                                .payingWith(DEFAULT_CONTRACT_SENDER)
                                .hasKnownStatus(SUCCESS)
                                .via(CREATE_TX))
                .then(
                        QueryVerbs.getAccountInfo(DEFAULT_CONTRACT_SENDER)
                                .savingSnapshot(DEFAULT_CONTRACT_SENDER),
                        QueryVerbs.getAccountInfo(DEFAULT_CONTRACT_RECEIVER)
                                .savingSnapshot(DEFAULT_CONTRACT_RECEIVER),
                        withOpContext(
                                (spec, log) -> {
                                    final var contractCreatorId =
                                            spec.registry()
                                                    .getAccountInfo(DEFAULT_CONTRACT_SENDER)
                                                    .getContractAccountID();
                                    final var nftSenderId =
                                            spec.registry()
                                                    .getAccountInfo(DEFAULT_CONTRACT_RECEIVER)
                                                    .getContractAccountID();

                                    final var mintParams =
                                            new Object[] {asHeadlongAddress(nftSenderId), nftId};
                                    final var approveParams =
                                            new Object[] {
                                                asHeadlongAddress(contractCreatorId), nftId
                                            };
                                    final var transferFromParams =
                                            new Object[] {
                                                asHeadlongAddress(nftSenderId),
                                                asHeadlongAddress(contractCreatorId),
                                                nftId
                                            };

                                    final var mint =
                                            contractCall(CONTRACT, "mint", mintParams)
                                                    .payingWith(DEFAULT_CONTRACT_SENDER)
                                                    .via(MINT_TX);
                                    allRunFor(spec, mint);

                                    final var approve =
                                            contractCall(CONTRACT, "approve", approveParams)
                                                    .payingWith(DEFAULT_CONTRACT_RECEIVER)
                                                    .signingWith(SECP_256K1_RECEIVER_SOURCE_KEY)
                                                    .gas(4_000_000L)
                                                    .via(APPROVE_TX);
                                    allRunFor(spec, approve);

                                    final var transferFrom =
                                            contractCall(
                                                            CONTRACT,
                                                            "transferFrom",
                                                            transferFromParams)
                                                    .payingWith(DEFAULT_CONTRACT_SENDER)
                                                    .via(TRANSFER_FROM_TX);
                                    allRunFor(spec, transferFrom);
                                }),
                        QueryVerbs.getTxnRecord(CREATE_TX),
                        QueryVerbs.getTxnRecord(MINT_TX),
                        QueryVerbs.getTxnRecord(APPROVE_TX),
                        QueryVerbs.getTxnRecord(TRANSFER_FROM_TX));
    }
}
