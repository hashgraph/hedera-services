package com.hedera.services.bdd.suites.records;

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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransferList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;

/**
 * This suite fetches the entities mentioned in the config file passed in the arguments and verifies if
 * they are valid/persent and performs further operations on them.
 */
public class MigrationValidationPostSteps extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(MigrationValidationPostSteps.class);

    final int VALUE_TO_SET = 123;
    final long amount1 = 100L;
    final long amount2 = 200L;

    public static final String MIGRATION_FILE = HapiSpecSetup.getDefaultInstance().migrationFileName();
    public static final String MIGRATION_ACCOUNT_A = HapiSpecSetup.getDefaultInstance().migrationAccountAName();
    public static final String MIGRATION_ACCOUNT_B = HapiSpecSetup.getDefaultInstance().migrationAccountBName();
    public static final String MIGRATION_SMART_CONTRACT = HapiSpecSetup.getDefaultInstance().migrationSmartContractName();

    final String smartContractRecords = "smartContractRecords";
    final String smartContractInfo = "contractInfo";
    final String cryptoRecordsA = "cryptoRecordsA";
    final String cryptoRecordsB = "cryptoRecordsB";
    final String SC_getValue = "getValue";

    static String migrationPropertyPath = "";

    final String fileContents = "MigrationValidationTestingHederaService";
    final byte[] old4K = fileContents.getBytes();

    public static void main(String... args) {

        if(args.length == 0){
            log.info("using default IDs for cryptoAccounts, files and smartContracts -- RUNNING CUSTOM NET MIGRATION ");
            migrationPropertyPath = "migration_config_default";
        }
        else{
            migrationPropertyPath = args[0];
        }

        new MigrationValidationPostSteps().runSuiteSync();
    }

    @Override
    protected List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                new HapiApiSpec[] {
                        migrationPreservesEntitiesPostStep()
                }
        );
    }

    /**
     * Builds a spec in which it...
     * 1. Validate the file contents after migration
     * 2. conduct some crypto transfers among the test accounts and verify they are successful;
     * 3. run the same smart contract to verify successful runs;
     *
     * @return the spec.
     */
    private HapiApiSpec migrationPreservesEntitiesPostStep() {

        return HapiApiSpec.customHapiSpec("migrationPreservesEntitiesPostStep")
                .withProperties(
                        migrationPropertyPath
                ).given()
                .when()
                .then(
                        UtilVerbs.blockingOrder(verifyFileActions()),
                        UtilVerbs.blockingOrder(verifyCryptoActions()),
                        UtilVerbs.blockingOrder(verifySmartContractActions())
                );
    }

    private HapiSpecOperation[] verifySmartContractActions() {
        return new HapiSpecOperation[] {
                TxnVerbs.contractCall(MIGRATION_SMART_CONTRACT, ContractResources.SIMPLE_STORAGE_SETTER_ABI, VALUE_TO_SET + 1).gas(15000),
                sleepFor(2_000L),
                TxnVerbs.contractCall(MIGRATION_SMART_CONTRACT, ContractResources.SIMPLE_STORAGE_GETTER_ABI).via(SC_getValue).gas(20000),
                sleepFor(2_000L),
                QueryVerbs.getTxnRecord(SC_getValue).hasPriority(TransactionRecordAsserts.recordWith().contractCallResult(
                        ContractFnResultAsserts.resultWith().resultThruAbi(ContractResources.SIMPLE_STORAGE_GETTER_ABI, ContractFnResultAsserts.isLiteralResult(
                                new Object[]{
                                        BigInteger.valueOf(VALUE_TO_SET+1)
                                }
                        ))
                ))
        };
    }

    private HapiSpecOperation[] verifyCryptoActions() {
        return new HapiSpecOperation[] {
                TxnVerbs.cryptoTransfer(tinyBarsFromTo(MIGRATION_ACCOUNT_A, MIGRATION_ACCOUNT_B, amount1)),
                sleepFor(2_000L),
                TxnVerbs.cryptoTransfer(tinyBarsFromTo(MIGRATION_ACCOUNT_B, MIGRATION_ACCOUNT_A, amount2)),
                sleepFor(2_000L)
        };
    }

    private HapiSpecOperation[] verifyFileActions() {
        return new HapiSpecOperation[] {
                QueryVerbs.getFileContents(MIGRATION_FILE).hasContents(ignore -> old4K),
                sleepFor(2_000L),
        };
    }

    private HapiSpecOperation[] verifyRecords() {
        return new HapiSpecOperation[]{
                QueryVerbs.getContractRecords(MIGRATION_SMART_CONTRACT)
                        .checkingAgainst(smartContractRecords),
                sleepFor(2_000L),
                QueryVerbs.getContractInfo(MIGRATION_SMART_CONTRACT, true).checkingAgainst(smartContractInfo),
                sleepFor(2_000L),
                QueryVerbs.getAccountRecords(MIGRATION_ACCOUNT_A).checkingAgainst(cryptoRecordsA),
                sleepFor(2_000L),
                QueryVerbs.getAccountRecords(MIGRATION_ACCOUNT_B).checkingAgainst(cryptoRecordsB),
                sleepFor(2_000L)
        };
    }

    public static Function<HapiApiSpec, TransferList> tinyBarsFromTo(String from, String to, long amount) {
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

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
