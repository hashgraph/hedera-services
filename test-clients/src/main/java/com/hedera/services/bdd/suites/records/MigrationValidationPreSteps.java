package com.hedera.services.bdd.suites.records;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.google.common.io.CharSink;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.props.NodeConnectInfo;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.legacy.core.TestHelper;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.TransferList;

import java.nio.charset.Charset;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//import org.bouncycastle.util.encoders.UTF8;

import java.io.File;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;

/**
 * This suite performs the operations to create various entities against a hedera network and saves the context.
 */
public class MigrationValidationPreSteps extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(MigrationValidationPreSteps.class);

    final String PATH_TO_SIMPLE_STORAGE_BYTECODE = "src/main/resource/testfiles/simpleStorage.bin";
    final String SC_GET_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"get\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
    final String SC_SET_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}],\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

    final int VALUE_TO_SET = 123;
    final long amount1 = 100L;
    final long amount2 = 200L;
    public static final String MIGRATION_FILE = HapiSpecSetup.getDefaultInstance().migrationFileName();
    public static final String MIGRATION_ACCOUNT_A = HapiSpecSetup.getDefaultInstance().migrationAccountAName();
    public static final String MIGRATION_ACCOUNT_B = HapiSpecSetup.getDefaultInstance().migrationAccountBName();
    public static final String MIGRATION_SMART_CONTRACT = HapiSpecSetup.getDefaultInstance().migrationSmartContractName();
    final String smartContractFileName = "simpleStorageSmartContractFile";
    final String smartContractRecords = "smartContractRecords";
    final String smartContractInfo = "contractInfo";
    final String cryptoRecordsA = "cryptoRecordsA";
    final String cryptoRecordsB = "cryptoRecordsB";
    final String SC_getValue = "getValue";

    final String fileContents = "MigrationValidationTestingHederaService";
    final byte[] old4K = fileContents.getBytes();

    public static void main(String... args) { new MigrationValidationPreSteps().runSuiteSync(); }

    @Override
    protected List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                new HapiApiSpec[] {
                        migrationPreservesEntitiesPreStep()
                }
        );
    }

    /**
     * Builds a spec in which it...
     * 1. create a File with some contents
     * 2. create a couple of crypto accounts and do some transaction between them
     * 3. create a smart contract and call it a few times.
     *
     * @return the spec.
     */
    private HapiApiSpec migrationPreservesEntitiesPreStep() {

        return defaultHapiSpec("migrationPreservesEntitiesPreStep")
                .given(
                        UtilVerbs.blockingOrder(doFileActions()),
                        UtilVerbs.blockingOrder(doCryptoActions()),
                        UtilVerbs.blockingOrder(doSmartContractActions())
                )
                .when(
//                        UtilVerbs.blockingOrder(recordActionsDone())
                )
                .then(
                        QueryVerbs.getFileInfo(MIGRATION_FILE).logged().saveToRegistry("migrationFile"),
                        QueryVerbs.getFileContents(MIGRATION_FILE).logged(),
                        QueryVerbs.getAccountInfo(MIGRATION_ACCOUNT_A).logged().saveToRegistry("migrationAccountA"),
                        QueryVerbs.getAccountInfo(MIGRATION_ACCOUNT_B).logged().saveToRegistry("migrationAccountB"),
                        QueryVerbs.getContractInfo(MIGRATION_SMART_CONTRACT).logged().saveToRegistry("migrationContract"),
                        UtilVerbs.withOpContext((spec, ctxLog ) -> {
                            FileWriter fw = null;
                            try {
                                fw = new FileWriter("src/main/resource/migration_config_default.properties");
                                ctxLog.info("File in resources");
                                AccountID id = spec.setup().genesisAccount();
                                String tmpId = asEntityId(id.getRealmNum(),id.getShardNum(), id.getAccountNum());
                                fw.write("default.payer=" + tmpId + "\n");

                                FileGetInfoResponse.FileInfo fileInfo = spec.registry().getFileInfo("migrationFile");
                                tmpId = asEntityId(fileInfo.getFileID().getRealmNum(), fileInfo.getFileID().getShardNum(),fileInfo.getFileID().getFileNum());
                                fw.write("migration.file.id=" + tmpId + "\n");

                                CryptoGetInfoResponse.AccountInfo accountInfoA = spec.registry().getAccountInfo("migrationAccountA");
                                CryptoGetInfoResponse.AccountInfo accountInfoB = spec.registry().getAccountInfo("migrationAccountB");
                                tmpId = asEntityId(accountInfoA.getAccountID().getRealmNum(),accountInfoA.getAccountID().getShardNum(), accountInfoA.getAccountID().getAccountNum());
                                fw.write("migration.crypto.AccountA.id=" + tmpId + "\n"  );
                                tmpId = asEntityId(accountInfoB.getAccountID().getRealmNum(),accountInfoB.getAccountID().getShardNum(), accountInfoB.getAccountID().getAccountNum());
                                fw.write("migration.crypto.AccountB.id=" + tmpId + "\n");

                                ContractGetInfoResponse.ContractInfo contractInfo = spec.registry().getContractInfo("migrationContract");
                                tmpId = asEntityId(contractInfo.getContractID().getRealmNum(),contractInfo.getContractID().getShardNum(), contractInfo.getContractID().getContractNum());
                                fw.write("migration.smartContract.id=" + tmpId + "\n");

                                fw.write("nodes=" + spec.setup().nodes().stream().map(NodeConnectInfo::uri).
                                        collect(Collectors.joining(" ")));

                                fw.flush();
                            } catch (IOException e) {
                                ctxLog.warn("Something wrong with File operation: {} ", e);
                            } catch (Exception e) {
                                ctxLog.warn("Unexpected error: {}", e);
                            } finally {
                                fw.close();
                            }

                        })
                )
                .saveContext(true);
    }

    private HapiSpecOperation[] recordActionsDone() {
        return new HapiSpecOperation[]{
                QueryVerbs.getContractRecords(MIGRATION_SMART_CONTRACT)
                        .savingTo(smartContractRecords),
                sleepFor(2_000L),
                QueryVerbs.getContractInfo(MIGRATION_SMART_CONTRACT).savingTo(smartContractInfo),
                sleepFor(2_000L),
                QueryVerbs.getAccountRecords(MIGRATION_ACCOUNT_A).savingTo(cryptoRecordsA),
                sleepFor(2_000L),
                QueryVerbs.getAccountRecords(MIGRATION_ACCOUNT_B).savingTo(cryptoRecordsB),
                sleepFor(2_000L)
        };
    }

    private HapiSpecOperation[] doSmartContractActions() {
        return new HapiSpecOperation[]{
                TxnVerbs.fileCreate(smartContractFileName)
                        .path(PATH_TO_SIMPLE_STORAGE_BYTECODE).key(GENESIS),
                sleepFor(2_000L),
                TxnVerbs.contractCreate(MIGRATION_SMART_CONTRACT).bytecode(smartContractFileName).adminKey(GENESIS).fee(20_00_000_000L),
                sleepFor(2_000L),
                TxnVerbs.contractCall(MIGRATION_SMART_CONTRACT, SC_SET_ABI, VALUE_TO_SET),
                sleepFor(2_000L),
                TxnVerbs.contractCall(MIGRATION_SMART_CONTRACT, SC_GET_ABI).via(SC_getValue),
                sleepFor(2_000L),
                QueryVerbs.getTxnRecord(SC_getValue).hasPriority(TransactionRecordAsserts.recordWith().contractCallResult(
                        ContractFnResultAsserts.resultWith().resultThruAbi(SC_GET_ABI, ContractFnResultAsserts.isLiteralResult(
                                new Object[]{
                                        BigInteger.valueOf(VALUE_TO_SET)
                                }
                        ))
                ))
        };
    }

    private HapiSpecOperation[] doCryptoActions() {
        return new HapiSpecOperation[]{
                TxnVerbs.cryptoCreate(MIGRATION_ACCOUNT_A).balance(1000L).key(GENESIS),
                sleepFor(2_000L),
                TxnVerbs.cryptoCreate(MIGRATION_ACCOUNT_B).balance(2000L).key(GENESIS),
                sleepFor(2_000L),
                TxnVerbs.cryptoTransfer(tinyBarsFromTo(MIGRATION_ACCOUNT_A, MIGRATION_ACCOUNT_B, amount1)),
                sleepFor(2_000L),
                TxnVerbs.cryptoTransfer(tinyBarsFromTo(MIGRATION_ACCOUNT_B, MIGRATION_ACCOUNT_A, amount2)),
                sleepFor(2_000L)
        };
    }

    private HapiSpecOperation[] doFileActions() {
        return new HapiSpecOperation[] {
                TxnVerbs.fileCreate(MIGRATION_FILE).contents(old4K).key(GENESIS),
                sleepFor(2_000L)
        };
    }

    private static Function<HapiApiSpec, TransferList> tinyBarsFromTo(String from, String to, long amount) {
        return tinyBarsFromTo(from, to, ignore -> amount);
    }

    public static Function<HapiApiSpec, TransferList> tinyBarsFromTo(
            String from, String to, Function<HapiApiSpec, Long> amountFn) {
        return spec -> {
            long amount = amountFn.apply(spec);
            AccountID toAccount = spec.registry().getAccountID(to);
            AccountID fromAccount = spec.registry().getAccountID(from);
            return TransferList.newBuilder()
                    .addAllAccountAmounts(Arrays.asList(
                            AccountAmount.newBuilder().setAccountID(toAccount).setAmount(amount).build(),
                            AccountAmount.newBuilder().setAccountID(fromAccount).setAmount(-1L * amount).build())).build();
        };
    }

    private String asEntityId(long realmId, long shardId, long num) {
        return String.format("%d.%d.%d", realmId,shardId,num);
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
