/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddressInTopic;
import static com.hedera.services.bdd.suites.contract.Utils.eventSignatureOf;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.swirlds.common.utility.CommonUtils.unhex;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import java.math.BigInteger;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class ERC20ContractInteractions extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(ERC20ContractInteractions.class);
    private static final String TRANSFER = "transfer";
    private static final String TRANSFER_FROM = "transferFrom";

    public static void main(String[] args) {
        new ERC20ContractInteractions().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(callsERC20ContractInteractions());
    }

    private HapiApiSpec callsERC20ContractInteractions() {
        final var CONTRACT = "GLDToken";
        final var CREATE_TX = "create";
        final var APPROVE_TX = "approve";
        final var TRANSFER_FROM_TX = "transferFromTxn";
        final var TRANSFER_MORE_THAN_APPROVED_FROM_TX = "transferMoreThanApproved";
        final var TRANSFER_TX = TRANSFER;
        final var NOT_ENOUGH_BALANCE_TRANSFER_TX = "notEnoughBalanceTransfer";
        final var amount = BigInteger.valueOf(1_000);
        final var initialAmount = BigInteger.valueOf(5_000);

        return defaultHapiSpec("callsERC20ContractInteractions")
                .given(
                        getAccountBalance(DEFAULT_CONTRACT_SENDER).logged(),
                        uploadInitCode(CONTRACT))
                .when(
                        getAccountBalance(DEFAULT_CONTRACT_SENDER).logged(),
                        contractCreate(CONTRACT, initialAmount)
                                .payingWith(DEFAULT_CONTRACT_SENDER)
                                .hasKnownStatus(SUCCESS)
                                .via(CREATE_TX)
                                .scrambleTxnBody(
                                        tx -> {
                                            System.out.println(
                                                    " tx - " + Bytes.wrap(tx.toByteArray()));
                                            return tx;
                                        }),
                        getAccountBalance(DEFAULT_CONTRACT_SENDER).logged())
                .then(
                        getAccountInfo(DEFAULT_CONTRACT_SENDER)
                                .savingSnapshot(DEFAULT_CONTRACT_SENDER),
                        getAccountInfo(DEFAULT_CONTRACT_RECEIVER)
                                .savingSnapshot(DEFAULT_CONTRACT_RECEIVER),
                        withOpContext(
                                (spec, log) -> {
                                    final var ownerInfo =
                                            spec.registry().getAccountInfo(DEFAULT_CONTRACT_SENDER);
                                    final var receiverInfo =
                                            spec.registry()
                                                    .getAccountInfo(DEFAULT_CONTRACT_RECEIVER);
                                    final var ownerContractId = ownerInfo.getContractAccountID();
                                    final var receiverContractId =
                                            receiverInfo.getContractAccountID();

                                    final var transferParams =
                                            new Object[] {
                                                asHeadlongAddress(receiverContractId), amount
                                            };
                                    final var notEnoughBalanceTransferParams =
                                            new Object[] {
                                                asHeadlongAddress(receiverContractId),
                                                initialAmount.subtract(amount).add(BigInteger.ONE)
                                            };
                                    final var approveParams =
                                            new Object[] {
                                                asHeadlongAddress(receiverContractId), amount
                                            };
                                    final var transferFromParams =
                                            new Object[] {
                                                asHeadlongAddress(ownerContractId),
                                                asHeadlongAddress(receiverContractId),
                                                amount
                                            };
                                    final var transferMoreThanApprovedFromParams =
                                            new Object[] {
                                                asHeadlongAddress(ownerContractId),
                                                asHeadlongAddress(receiverContractId),
                                                amount.add(BigInteger.ONE)
                                            };

                                    final var transfer =
                                            contractCall(CONTRACT, TRANSFER, transferParams)
                                                    .payingWith(DEFAULT_CONTRACT_SENDER)
                                                    .via(TRANSFER_TX)
                                                    .scrambleTxnBody(
                                                            tx -> {
                                                                System.out.println(
                                                                        " tx - "
                                                                                + Bytes.wrap(
                                                                                        tx
                                                                                                .toByteArray()));
                                                                return tx;
                                                            });

                                    final var notEnoughBalanceTransfer =
                                            contractCall(
                                                            CONTRACT,
                                                            TRANSFER,
                                                            notEnoughBalanceTransferParams)
                                                    .payingWith(DEFAULT_CONTRACT_SENDER)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                    .via(NOT_ENOUGH_BALANCE_TRANSFER_TX)
                                                    .scrambleTxnBody(
                                                            tx -> {
                                                                System.out.println(
                                                                        " tx - "
                                                                                + Bytes.wrap(
                                                                                        tx
                                                                                                .toByteArray()));
                                                                return tx;
                                                            });

                                    final var approve =
                                            contractCall(CONTRACT, "approve", approveParams)
                                                    .payingWith(DEFAULT_CONTRACT_SENDER)
                                                    .via(APPROVE_TX)
                                                    .scrambleTxnBody(
                                                            tx -> {
                                                                System.out.println(
                                                                        " tx - "
                                                                                + Bytes.wrap(
                                                                                        tx
                                                                                                .toByteArray()));
                                                                return tx;
                                                            });

                                    final var transferFrom =
                                            contractCall(
                                                            CONTRACT,
                                                            TRANSFER_FROM,
                                                            transferFromParams)
                                                    .payingWith(DEFAULT_CONTRACT_RECEIVER)
                                                    .signingWith(SECP_256K1_RECEIVER_SOURCE_KEY)
                                                    .via(TRANSFER_FROM_TX)
                                                    .scrambleTxnBody(
                                                            tx -> {
                                                                System.out.println(
                                                                        " tx - "
                                                                                + Bytes.wrap(
                                                                                        tx
                                                                                                .toByteArray()));
                                                                return tx;
                                                            });

                                    final var transferMoreThanApprovedFrom =
                                            contractCall(
                                                            CONTRACT,
                                                            TRANSFER_FROM,
                                                            transferMoreThanApprovedFromParams)
                                                    .payingWith(DEFAULT_CONTRACT_RECEIVER)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                    .via(TRANSFER_MORE_THAN_APPROVED_FROM_TX)
                                                    .scrambleTxnBody(
                                                            tx -> {
                                                                System.out.println(
                                                                        " tx - "
                                                                                + Bytes.wrap(
                                                                                        tx
                                                                                                .toByteArray()));
                                                                return tx;
                                                            });

                                    final var getCreateRecord =
                                            getTxnRecord(CREATE_TX)
                                                    .hasPriority(
                                                            recordWith()
                                                                    .contractCreateResult(
                                                                            resultWith()
                                                                                    .logs(
                                                                                            inOrder(
                                                                                                    logWith()
                                                                                                            .longValue(
                                                                                                                    initialAmount
                                                                                                                            .longValueExact())
                                                                                                            .withTopicsInOrder(
                                                                                                                    List
                                                                                                                            .of(
                                                                                                                                    eventSignatureOf(
                                                                                                                                            "Transfer(address,address,uint256)"),
                                                                                                                                    parsedToByteString(
                                                                                                                                            0),
                                                                                                                                    ByteString
                                                                                                                                            .copyFrom(
                                                                                                                                                    asAddressInTopic(
                                                                                                                                                            unhex(
                                                                                                                                                                    ownerInfo
                                                                                                                                                                            .getContractAccountID())))))))))
                                                    .logged();
                                    final var getTransferRecord =
                                            getTxnRecord(TRANSFER_TX)
                                                    .exposingTo(
                                                            tr ->
                                                                    System.out.println(
                                                                            Bytes.of(
                                                                                    tr
                                                                                            .toByteArray())))
                                                    .hasPriority(
                                                            recordWith()
                                                                    .contractCallResult(
                                                                            resultWith()
                                                                                    .logs(
                                                                                            inOrder(
                                                                                                    logWith()
                                                                                                            .longValue(
                                                                                                                    amount
                                                                                                                            .longValueExact())
                                                                                                            .withTopicsInOrder(
                                                                                                                    List
                                                                                                                            .of(
                                                                                                                                    eventSignatureOf(
                                                                                                                                            "Transfer(address,address,uint256)"),
                                                                                                                                    ByteString
                                                                                                                                            .copyFrom(
                                                                                                                                                    asAddressInTopic(
                                                                                                                                                            unhex(
                                                                                                                                                                    ownerInfo
                                                                                                                                                                            .getContractAccountID()))),
                                                                                                                                    ByteString
                                                                                                                                            .copyFrom(
                                                                                                                                                    asAddressInTopic(
                                                                                                                                                            unhex(
                                                                                                                                                                    receiverInfo
                                                                                                                                                                            .getContractAccountID())))))))))
                                                    .logged();
                                    final var getApproveRecord =
                                            getTxnRecord(APPROVE_TX)
                                                    .exposingTo(
                                                            tr ->
                                                                    System.out.println(
                                                                            Bytes.of(
                                                                                    tr
                                                                                            .toByteArray())))
                                                    .hasPriority(
                                                            recordWith()
                                                                    .contractCallResult(
                                                                            resultWith()
                                                                                    .logs(
                                                                                            inOrder(
                                                                                                    logWith()
                                                                                                            .longValue(
                                                                                                                    amount
                                                                                                                            .longValueExact())
                                                                                                            .withTopicsInOrder(
                                                                                                                    List
                                                                                                                            .of(
                                                                                                                                    eventSignatureOf(
                                                                                                                                            "Approval(address,address,uint256)"),
                                                                                                                                    ByteString
                                                                                                                                            .copyFrom(
                                                                                                                                                    asAddressInTopic(
                                                                                                                                                            unhex(
                                                                                                                                                                    ownerInfo
                                                                                                                                                                            .getContractAccountID()))),
                                                                                                                                    ByteString
                                                                                                                                            .copyFrom(
                                                                                                                                                    asAddressInTopic(
                                                                                                                                                            unhex(
                                                                                                                                                                    receiverInfo
                                                                                                                                                                            .getContractAccountID())))))))))
                                                    .logged();
                                    final var getTransferFromRecord =
                                            getTxnRecord(TRANSFER_FROM_TX)
                                                    .exposingTo(
                                                            tr ->
                                                                    System.out.println(
                                                                            Bytes.of(
                                                                                    tr
                                                                                            .toByteArray())))
                                                    .hasPriority(
                                                            recordWith()
                                                                    .contractCallResult(
                                                                            resultWith()
                                                                                    .logs(
                                                                                            inOrder(
                                                                                                    logWith()
                                                                                                            .longValue(
                                                                                                                    amount
                                                                                                                            .longValueExact())
                                                                                                            .withTopicsInOrder(
                                                                                                                    List
                                                                                                                            .of(
                                                                                                                                    eventSignatureOf(
                                                                                                                                            "Transfer(address,address,uint256)"),
                                                                                                                                    ByteString
                                                                                                                                            .copyFrom(
                                                                                                                                                    asAddressInTopic(
                                                                                                                                                            unhex(
                                                                                                                                                                    ownerInfo
                                                                                                                                                                            .getContractAccountID()))),
                                                                                                                                    ByteString
                                                                                                                                            .copyFrom(
                                                                                                                                                    asAddressInTopic(
                                                                                                                                                            unhex(
                                                                                                                                                                    receiverInfo
                                                                                                                                                                            .getContractAccountID()))))),
                                                                                                    logWith()
                                                                                                            .longValue(
                                                                                                                    0)
                                                                                                            .withTopicsInOrder(
                                                                                                                    List
                                                                                                                            .of(
                                                                                                                                    eventSignatureOf(
                                                                                                                                            "Approval(address,address,uint256)"),
                                                                                                                                    ByteString
                                                                                                                                            .copyFrom(
                                                                                                                                                    asAddressInTopic(
                                                                                                                                                            unhex(
                                                                                                                                                                    ownerInfo
                                                                                                                                                                            .getContractAccountID()))),
                                                                                                                                    ByteString
                                                                                                                                            .copyFrom(
                                                                                                                                                    asAddressInTopic(
                                                                                                                                                            unhex(
                                                                                                                                                                    receiverInfo
                                                                                                                                                                            .getContractAccountID())))))))))
                                                    .logged();

                                    final var getNotEnoughBalanceTransferRecord =
                                            getTxnRecord(NOT_ENOUGH_BALANCE_TRANSFER_TX)
                                                    .exposingTo(
                                                            tr ->
                                                                    System.out.println(
                                                                            Bytes.of(
                                                                                    tr
                                                                                            .toByteArray())));
                                    final var transferMoreThanApprovedRecord =
                                            getTxnRecord(TRANSFER_MORE_THAN_APPROVED_FROM_TX)
                                                    .exposingTo(
                                                            tr ->
                                                                    System.out.println(
                                                                            Bytes.of(
                                                                                    tr
                                                                                            .toByteArray())));

                                    allRunFor(
                                            spec,
                                            transfer,
                                            notEnoughBalanceTransfer,
                                            approve,
                                            transferMoreThanApprovedFrom,
                                            transferFrom,
                                            getCreateRecord,
                                            getTransferRecord,
                                            getApproveRecord,
                                            getTransferFromRecord,
                                            getNotEnoughBalanceTransferRecord,
                                            transferMoreThanApprovedRecord);
                                }));
    }
}
