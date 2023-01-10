/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.misc;

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromMnemonic;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromPem;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.suites.HapiSuite;
import com.swirlds.common.utility.CommonUtils;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WalletTestSetup extends HapiSuite {
    private static final Logger log = LogManager.getLogger(WalletTestSetup.class);

    static String DETERMINISTIC_WALLET = "deterministicWallet";

    static String mnemonic =
            "girl adjust asset interest razor thrive "
                    + "joy diet stock radar home because "
                    + "sausage culture fitness damage vicious "
                    + "target cabin best stomach replace "
                    + "example ordinary";

    public static void main(String... args) throws Exception {
        new WalletTestSetup().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    //						createDeterministicWalletForRecovery(),
                    //						reviewDeterministicWallet(),
                    //						fundDeterministicWallet(),
                    mnemonicToPem(),
                });
    }

    private HapiSpec mnemonicToPem() {
        return defaultHapiSpec("MnemonicToPem")
                .given(keyFromMnemonic("fm", mnemonic))
                .when()
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    KeyFactory.PEM_PASSPHRASE = "guessAgain";
                                    spec.keys()
                                            .exportSimpleKey(
                                                    String.format("pretend-genesis.pem"), "fm");
                                }));
    }

    private HapiSpec createDeterministicWalletForRecovery() {
        return customHapiSpec("CreateDeterministicWalletForRecovery")
                .withProperties(
                        Map.of(
                                //						"nodes", "35.237.200.180:0.0.3",
                                "client.feeSchedule.fromDisk", "true",
                                "client.feeSchedule.path", "system-files/feeSchedule.bin",
                                "client.exchangeRates.fromDisk", "true",
                                "client.exchangeRates.path", "system-files/exchangeRates.bin",
                                //						"nodes",
                                // "35.237.182.66:0.0.3,35.245.226.22:0.0.4,34.68.9.203:0.0.5,34.83.131.197:0.0.6,34.94
                                //
                                //	.236.63:0.0.7,35.203.26.115:0.0.8,34.77.3.213:0.0.9,35.197.237.44:0.0.10,35.246.250.176:0.0.11,
                                //
                                //	34.90.117.105:0.0.12,35.200.57.21:0.0.13,34.92.120.143:0.0.14,34.87.47.168:0.0.15"
                                "nodes",
                                        "34.94.106.61:0.0.3,35.237.119.55:0.0.4,35.245.27.193:0.0.5,34.83.112.116:0.0.6"
                                //						"nodes",
                                // "35.237.182.66:0.0.3,35.245.226.22:0.0.4,34.68.9.203:0.0.5,34.83.131.197:0.0.6,34.94
                                //
                                //	.236.63:0.0.7,35.203.26.115:0.0.8,34.77.3.213:0.0.9,35.197.237.44:0.0.10,35.246.250.176:0.0.11,
                                //
                                //	34.90.117.105:0.0.12,35.200.57.21:0.0.13,34.92.120.143:0.0.14,34.87.47.168:0.0.15"
                                ))
                .given(
                        //						keyFromPem("src/main/resource/mainnet-account950.pem")
                        //								.passphrase("swirlds")
                        //								.name("payer")
                        //								.linkedTo("0.0.950"),
                        //						keyFromPem("src/main/resource/mainnet-account39280.pem")
                        //								.passphrase("swirlds")
                        //								.name("oldKey")
                        //								.linkedTo("0.0.39280"),
                        //						keyFromMnemonic(DETERMINISTIC_WALLET,
                        // stagingMnemonic).linkedTo("0.0.1115")
                        keyFromPem("<MASTER-PEM_LOC>")
                                .name("master")
                                .passphrase("<SECRET>")
                                .linkedTo("0.0.50"),
                        keyFromMnemonic(DETERMINISTIC_WALLET, mnemonic))
                .when()
                .then(
                        cryptoCreate("target").key(DETERMINISTIC_WALLET).payingWith("master")
                        //						cryptoTransfer(
                        //								HapiCryptoTransfer.tinyBarsFromTo(GENESIS, DETERMINISTIC_WALLET,
                        // 1_000_000 *
                        //								100_000_000L))
                        //						QueryVerbs.getAccountBalance("0.0.950"),
                        //						cryptoTransfer(
                        //								HapiCryptoTransfer.tinyBarsFromTo(
                        //										"payer",
                        //										DETERMINISTIC_WALLET,
                        //										5 * 100_000_000)).payingWith("payer")
                        //						cryptoUpdate("0.0.39280")
                        //								.key(DETERMINISTIC_WALLET)
                        //								.fee(10_000_000)
                        //								.payingWith("payer")
                        //								.signedBy("payer", "oldKey", DETERMINISTIC_WALLET)
                        //								.receiverSigRequired(false)
                        );
    }

    private HapiSpec fundDeterministicWallet() {
        long TINYBARS_PER_HBAR = 100_000_000L;
        long amount = 100_000 * TINYBARS_PER_HBAR;
        return customHapiSpec("FundDeterministicWallet")
                .withProperties(Map.of("nodes", "35.237.182.66:0.0.3"))
                .given(keyFromMnemonic(DETERMINISTIC_WALLET, mnemonic).linkedTo("0.0.1113"))
                .when(
                        cryptoTransfer(
                                HapiCryptoTransfer.tinyBarsFromTo(
                                        GENESIS, DETERMINISTIC_WALLET, amount)))
                .then(QueryVerbs.getAccountBalance(GENESIS));
    }

    private HapiSpec reviewDeterministicWallet() {
        return customHapiSpec("ReviewDeterministicWallet")
                .withProperties(
                        Map.of(
                                "nodes",
                                "35.237.182.66:0.0.3,35.245.226.22:0.0.4,34.68.9.203:0.0.5,34.83.131.197:0.0.6,"
                                    + "34.94.236.63:0.0.7,35.203.26.115:0.0.8,34.77.3.213:0.0.9,35.197.237.44:0.0.10,"
                                    + "35.246.250.176:0.0.11,34.90.117.105:0.0.12,35.200.57.21:0.0.13,34.92.120.143:0.0.14,"
                                    + "34.87.47.168:0.0.15"))
                .given(keyFromMnemonic(DETERMINISTIC_WALLET, mnemonic).linkedTo("0.0.1113"))
                .when(
                        cryptoTransfer(
                                        HapiCryptoTransfer.tinyBarsFromTo(
                                                DETERMINISTIC_WALLET, GENESIS, 1))
                                .payingWith(DETERMINISTIC_WALLET))
                .then(
                        QueryVerbs.getAccountInfo("0.0.1113")
                                .logged()
                                .plusCustomLog(
                                        (info, opLog) ->
                                                opLog.info(
                                                        CommonUtils.hex(
                                                                info.getKey()
                                                                        .getEd25519()
                                                                        .toByteArray()))));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
