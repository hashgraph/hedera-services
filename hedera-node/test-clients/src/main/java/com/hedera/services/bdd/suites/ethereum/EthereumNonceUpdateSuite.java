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

package com.hedera.services.bdd.suites.ethereum;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.WRONG_NONCE;

import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite
@Tag(SMART_CONTRACT)
public class EthereumNonceUpdateSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(EthereumSuite.class);
    private final String INTERNAL_CALLEE_CONTRACT = "InternalCallee";
    private final String EXTERNAL_FUNCTION = "externalFunction";

    public static void main(String... args) {
        new EthereumSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(nonceNotUpdatedOnHandlerCheckFailed(), nonceUpdatedAfterSuccessfulExecutedTx());
    }

    @HapiTest
    private HapiSpec nonceNotUpdatedOnHandlerCheckFailed() {
        final String NON_EXISTING_ACCOUNT_KEY = "nonExistingAccount";

        return defaultHapiSpec("nonceNotUpdatedOnHandlerCheckFailed")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(NON_EXISTING_ACCOUNT_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HBAR)),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT),
                        contractCreate(INTERNAL_CALLEE_CONTRACT))
                .when(
                        ethereumCall(INTERNAL_CALLEE_CONTRACT, EXTERNAL_FUNCTION)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .gasLimit(21_000L)
                                .hasKnownStatus(ResponseCodeEnum.INSUFFICIENT_GAS),
                        ethereumCall(INTERNAL_CALLEE_CONTRACT, EXTERNAL_FUNCTION)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .gasPrice(10L)
                                .sending(ONE_HUNDRED_HBARS)
                                .gasLimit(1_000_000L)
                                .hasKnownStatus(INSUFFICIENT_PAYER_BALANCE),
                        ethereumCall(INTERNAL_CALLEE_CONTRACT, EXTERNAL_FUNCTION)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(5)
                                .gasPrice(10L)
                                .gasLimit(1_000_000L)
                                .hasKnownStatus(WRONG_NONCE),
                        ethereumCall(INTERNAL_CALLEE_CONTRACT, EXTERNAL_FUNCTION)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(NON_EXISTING_ACCOUNT_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .gasPrice(10L)
                                .gasLimit(1_000_000L)
                                .hasKnownStatus(INVALID_ACCOUNT_ID)
                )
                .then(getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                        .has(accountWith().nonce(0L)));
    }

    @HapiTest
    private HapiSpec nonceUpdatedAfterSuccessfulExecutedTx() {
        return defaultHapiSpec("nonceUpdatedAfterSuccessfulExecutedTx")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HBAR)),
                        uploadInitCode(INTERNAL_CALLEE_CONTRACT),
                        contractCreate(INTERNAL_CALLEE_CONTRACT))
                .when(ethereumCall(INTERNAL_CALLEE_CONTRACT, EXTERNAL_FUNCTION)
                        .type(EthTxData.EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(RELAYER)
                        .nonce(0)
                        .gasLimit(100_000L))
                .then(getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                        .has(accountWith().nonce(1L)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
