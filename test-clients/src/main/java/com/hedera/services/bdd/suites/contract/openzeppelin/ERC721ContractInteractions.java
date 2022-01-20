package com.hedera.services.bdd.suites.contract.openzeppelin;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ERC721_APPROVE_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ERC721_BYTECODE_PATH;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ERC721_MINT_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ERC721_TRANSFER_FROM_ABI;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ERC721ContractInteractions extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(ERC721ContractInteractions.class);

    public static void main(String... args) {
        new ERC721ContractInteractions().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    protected List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                callsERC721ContractInteractions()
        );
    }

    private HapiApiSpec callsERC721ContractInteractions() {
        final var PAYER = "tx_payer";
        final var CONTRACT_CREATOR = "contractCreator";
        final var NFT_SENDER = "sender";
        final var CONTRACT_FILE_NAME = "ERC721ContractFile";
        final var NFT_ID = 1;

        final var CREATE_TX = "create";
        final var MINT_TX = "mint";
        final var APPROVE_TX = "approve";
        final var TRANSFER_FROM_TX = "transferFrom";
        final var PAYER_KEY = "payerKey";
        final var FILE_KEY_LIST = "fileKeyList";

        return defaultHapiSpec("CallsERC721ContractInteractions")
                .given(
                        newKeyNamed(PAYER_KEY),
                        newKeyListNamed(FILE_KEY_LIST, List.of(PAYER_KEY)),
                        cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS).key(PAYER_KEY),
                        cryptoCreate(CONTRACT_CREATOR),
                        cryptoCreate(NFT_SENDER),
                        QueryVerbs.getAccountBalance(PAYER).logged(),
                        fileCreate(CONTRACT_FILE_NAME).payingWith(PAYER).key(FILE_KEY_LIST),
                        updateLargeFile(PAYER, CONTRACT_FILE_NAME, extractByteCode(ERC721_BYTECODE_PATH))
                ).when(
                        QueryVerbs.getAccountBalance(PAYER).logged(),
                        contractCreate("testContract")
                                .payingWith(CONTRACT_CREATOR)
                                .bytecode(CONTRACT_FILE_NAME)
                                .hasKnownStatus(SUCCESS)
                                .via(CREATE_TX)
                ).then(
                        QueryVerbs.getAccountInfo(CONTRACT_CREATOR).savingSnapshot(CONTRACT_CREATOR),
                        QueryVerbs.getAccountInfo(NFT_SENDER).savingSnapshot(NFT_SENDER),

                        withOpContext((spec, log) -> {
                            final var contractCreatorId = spec.registry().getAccountInfo(CONTRACT_CREATOR).getContractAccountID();
                            final var nftSenderId = spec.registry().getAccountInfo(NFT_SENDER).getContractAccountID();

                            final var mintParams = new Object[]{nftSenderId, NFT_ID};
                            final var approveParams = new Object[]{contractCreatorId, NFT_ID};
                            final var transferFromParams = new Object[]{nftSenderId, contractCreatorId, NFT_ID};

                            final var mint = contractCall("testContract",
                                    ERC721_MINT_ABI, mintParams).payingWith(CONTRACT_CREATOR).via(MINT_TX);
                            allRunFor(spec, mint);

                            final var approve = contractCall("testContract",
                                    ERC721_APPROVE_ABI, approveParams).payingWith(NFT_SENDER).via(APPROVE_TX);
                            allRunFor(spec, approve);

                            final var transferFrom = contractCall("testContract",
                                    ERC721_TRANSFER_FROM_ABI, transferFromParams).payingWith(CONTRACT_CREATOR).via(TRANSFER_FROM_TX);
                            allRunFor(spec, transferFrom);
                        }),
                        QueryVerbs.getTxnRecord(CREATE_TX).logged(),
                        QueryVerbs.getTxnRecord(MINT_TX).logged(),
                        QueryVerbs.getTxnRecord(APPROVE_TX).logged(),
                        QueryVerbs.getTxnRecord(TRANSFER_FROM_TX).logged()
                );
    }
}
