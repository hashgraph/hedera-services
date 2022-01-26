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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ERC20_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ERC20_APPROVE_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ERC20_BYTECODE_PATH;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ERC20_TRANSFER_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ERC20_TRANSFER_FROM_ABI;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.eventSignatureOf;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ERC20ContractInteractions extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(ERC20ContractInteractions.class);

    public static void main(String[] args) {
        new ERC20ContractInteractions().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    protected List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                callsERC20ContractInteractions()
        );
    }

    private HapiApiSpec callsERC20ContractInteractions() {
        final var OWNER = "owner";
        final var RECEIVER = "receiver";
        final var CONTRACT_FILE_NAME = "ERC20ContractFile";
        final var CREATE_TX = "create";
        final var APPROVE_TX = "approve";
        final var TRANSFER_FROM_TX = "transferFrom";
        final var TRANSFER_MORE_THAN_APPROVED_FROM_TX = "transferMoreThanApproved";
        final var TRANSFER_TX = "transfer";
        final var NOT_ENOUGH_BALANCE_TRANSFER_TX = "notEnoughBalanceTransfer";
        final var AMOUNT = 1_000;
        final var INITIAL_AMOUNT = 5_000;

        return defaultHapiSpec("callsERC20ContractInteractions")
                .given(
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER),
                        getAccountBalance(OWNER).logged(),
                        fileCreate(CONTRACT_FILE_NAME).payingWith(OWNER),
                        updateLargeFile(OWNER, CONTRACT_FILE_NAME, extractByteCode(ERC20_BYTECODE_PATH))
                ).when(
                        getAccountBalance(OWNER).logged(),
                        contractCreate("testContract", ERC20_ABI, INITIAL_AMOUNT)
                                .payingWith(OWNER)
                                .bytecode(CONTRACT_FILE_NAME)
                                .hasKnownStatus(SUCCESS)
                                .via(CREATE_TX),
                        getAccountBalance(OWNER).logged()
                ).then(
                        getAccountInfo(OWNER).savingSnapshot(OWNER),
                        getAccountInfo(RECEIVER).savingSnapshot(RECEIVER),

                        withOpContext((spec, log) -> {
                            final var ownerInfo = spec.registry().getAccountInfo(OWNER);
                            final var receiverInfo = spec.registry().getAccountInfo(RECEIVER);
                            final var ownerContractId = ownerInfo.getContractAccountID();
                            final var receiverContractId = receiverInfo.getContractAccountID();

                            final var transferParams = new Object[]{receiverContractId, AMOUNT};
                            final var notEnoughBalanceTransferParams = new Object[]{receiverContractId, INITIAL_AMOUNT - AMOUNT + 1};
                            final var approveParams = new Object[]{receiverContractId, AMOUNT};
                            final var transferFromParams = new Object[]{ownerContractId, receiverContractId, AMOUNT};
                            final var transferMoreThanApprovedFromParams = new Object[]{ownerContractId, receiverContractId, AMOUNT+1};

                            final var transfer = contractCall("testContract", ERC20_TRANSFER_ABI, transferParams).payingWith(OWNER).via(TRANSFER_TX);

                            final var notEnoughBalanceTransfer = contractCall("testContract", ERC20_TRANSFER_ABI, notEnoughBalanceTransferParams).payingWith(OWNER).hasKnownStatus(CONTRACT_REVERT_EXECUTED).via(NOT_ENOUGH_BALANCE_TRANSFER_TX);

                            final var approve = contractCall("testContract", ERC20_APPROVE_ABI, approveParams).payingWith(OWNER).via(APPROVE_TX);

                            final var transferFrom = contractCall("testContract", ERC20_TRANSFER_FROM_ABI, transferFromParams).payingWith(RECEIVER).via(TRANSFER_FROM_TX);

                            final var transferMoreThanApprovedFrom = contractCall("testContract", ERC20_TRANSFER_FROM_ABI, transferMoreThanApprovedFromParams).payingWith(RECEIVER).hasKnownStatus(CONTRACT_REVERT_EXECUTED).via(TRANSFER_MORE_THAN_APPROVED_FROM_TX);

                            final var getCreateRecord = getTxnRecord(CREATE_TX)
                                    .hasPriority(recordWith().contractCreateResult(
                                            resultWith().logs(
                                                    inOrder(
                                                            logWith()
                                                                    .longValue(INITIAL_AMOUNT)
                                                                    .withTopicsInOrder(
                                                                            List.of(
                                                                                    eventSignatureOf("Transfer(address,address,uint256)"),
                                                                                    parsedToByteString(0),
                                                                                    parsedToByteString(ownerInfo.getAccountID().getAccountNum())
                                                                            )
                                                                    )
                                                    )
                                            )
                                    ))
                                    .logged();
                            final var getTransferRecord = getTxnRecord(TRANSFER_TX)
                                    .hasPriority(recordWith().contractCallResult(
                                            resultWith().logs(
                                                    inOrder(
                                                            logWith()
                                                                    .longValue(AMOUNT)
                                                                    .withTopicsInOrder(
                                                                    List.of(
                                                                            eventSignatureOf("Transfer(address,address,uint256)"),
                                                                            parsedToByteString(ownerInfo.getAccountID().getAccountNum()),
                                                                            parsedToByteString(receiverInfo.getAccountID().getAccountNum())
                                                                    )
                                                            )
                                                    )
                                            )
                                    ))
                                    .logged();
                            final var getApproveRecord = getTxnRecord(APPROVE_TX)
                                    .hasPriority(recordWith().contractCallResult(
                                            resultWith().logs(
                                                    inOrder(
                                                            logWith()
                                                                    .longValue(AMOUNT)
                                                                    .withTopicsInOrder(
                                                                    List.of(
                                                                            eventSignatureOf("Approval(address,address,uint256)"),
                                                                            parsedToByteString(ownerInfo.getAccountID().getAccountNum()),
                                                                            parsedToByteString(receiverInfo.getAccountID().getAccountNum())
                                                                    )
                                                            )
                                                    )
                                            )
                                    ))
                                    .logged();
                            final var getTransferFromRecord = getTxnRecord(TRANSFER_FROM_TX)
                                    .hasPriority(recordWith().contractCallResult(
                                            resultWith().logs(
                                                    inOrder(
                                                            logWith()
                                                                    .longValue(AMOUNT)
                                                                    .withTopicsInOrder(
                                                                            List.of(
                                                                                    eventSignatureOf("Transfer(address,address,uint256)"),
                                                                                    parsedToByteString(ownerInfo.getAccountID().getAccountNum()),
                                                                                    parsedToByteString(receiverInfo.getAccountID().getAccountNum())
                                                                            )
                                                                    ),
                                                            logWith()
                                                                    .longValue(0)
                                                                    .withTopicsInOrder(
                                                                            List.of(
                                                                                    eventSignatureOf("Approval(address,address,uint256)"),
                                                                                    parsedToByteString(ownerInfo.getAccountID().getAccountNum()),
                                                                                    parsedToByteString(receiverInfo.getAccountID().getAccountNum())
                                                                            )
                                                                    )
                                                    )
                                            )
                                    ))
                                    .logged();

                            final var getNotEnoughBalanceTransferRecord = getTxnRecord(NOT_ENOUGH_BALANCE_TRANSFER_TX).logged();
                            final var transferMoreThanApprovedRecord = getTxnRecord(TRANSFER_MORE_THAN_APPROVED_FROM_TX).logged();

                            allRunFor(spec, transfer, notEnoughBalanceTransfer, approve, transferMoreThanApprovedFrom, transferFrom, getCreateRecord, getTransferRecord, getApproveRecord, getTransferFromRecord, getNotEnoughBalanceTransferRecord, transferMoreThanApprovedRecord);
                        })
                );
    }
}
