/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.contract.hapi;

import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;

import com.hedera.services.bdd.suites.HapiSuite;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

@SuppressWarnings("java:S1192") // "string literal should not be duplicated" - this rule makes test suites worse
public class ContractCreateV1SecurityModelSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(ContractCreateV1SecurityModelSuite.class);

    public static final String EMPTY_CONSTRUCTOR_CONTRACT = "EmptyConstructor";

    public static void main(String... args) {
        new ContractCreateV1SecurityModelSuite().runSuiteSync();
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(receiverSigReqTransferRecipientMustSignWithFullPubKeyPrefix());
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    final Stream<DynamicTest> receiverSigReqTransferRecipientMustSignWithFullPubKeyPrefix() {
        final var sendInternalAndDelegateContract = "SendInternalAndDelegate";
        final var justSendContract = "JustSend";
        final var beneficiary = "civilian";
        final var balanceToDistribute = 1_000L;

        final AtomicLong justSendContractNum = new AtomicLong();
        final AtomicLong beneficiaryAccountNum = new AtomicLong();

        return propertyPreservingHapiSpec("ReceiverSigReqTransferRecipientMustSignWithFullPubKeyPrefix")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        cryptoCreate(beneficiary)
                                .balance(0L)
                                .receiverSigRequired(true)
                                .exposingCreatedIdTo(id -> beneficiaryAccountNum.set(id.getAccountNum())),
                        uploadInitCode(sendInternalAndDelegateContract, justSendContract))
                .when(
                        contractCreate(justSendContract).gas(300_000L).exposingNumTo(justSendContractNum::set),
                        contractCreate(sendInternalAndDelegateContract)
                                .gas(300_000L)
                                .balance(balanceToDistribute))
                .then(
                        /* Sending requires receiver signature */
                        sourcing(() -> contractCall(
                                        sendInternalAndDelegateContract,
                                        "sendRepeatedlyTo",
                                        BigInteger.valueOf(justSendContractNum.get()),
                                        BigInteger.valueOf(beneficiaryAccountNum.get()),
                                        BigInteger.valueOf(balanceToDistribute / 2))
                                .hasKnownStatus(INVALID_SIGNATURE)),
                        /* But it's not enough to just sign using an incomplete prefix */
                        sourcing(() -> contractCall(
                                        sendInternalAndDelegateContract,
                                        "sendRepeatedlyTo",
                                        BigInteger.valueOf(justSendContractNum.get()),
                                        BigInteger.valueOf(beneficiaryAccountNum.get()),
                                        BigInteger.valueOf(balanceToDistribute / 2))
                                .signedBy(DEFAULT_PAYER, beneficiary)
                                .hasKnownStatus(INVALID_SIGNATURE)),
                        /* We have to specify the full prefix so the sig can be verified async */
                        getAccountInfo(beneficiary).logged(),
                        sourcing(() -> contractCall(
                                        sendInternalAndDelegateContract,
                                        "sendRepeatedlyTo",
                                        BigInteger.valueOf(justSendContractNum.get()),
                                        BigInteger.valueOf(beneficiaryAccountNum.get()),
                                        BigInteger.valueOf(balanceToDistribute / 2))
                                .alsoSigningWithFullPrefix(beneficiary)),
                        getAccountBalance(beneficiary).logged());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
