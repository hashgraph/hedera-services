package com.hedera.services.bdd.suites.contract.base;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.SIMPLE_STORAGE_CONTRACT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ContractBaseSuite extends HapiApiSuite {

    private static final Logger log = LogManager.getLogger(ContractBaseSuite.class);

    // LONG_MAX (9,223,372,036,854,775,807)

    public static void main(String... args) {
        new ContractBaseSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(new HapiApiSpec[] {
                successfulOperationsWithInteger(),
                successfulOperationsWithLong(),
                successfulOperationsWithBigInteger(),
                successfulOperationsWithAddress()
        });
    }

    private HapiApiSpec successfulOperationsWithInteger() {
        final var integerValue = 255;
        // final var integerValue = 256; Invalid integer

        return defaultHapiSpec("successfulOperationsWithInteger")
                .given(
                        uploadInitCode(SIMPLE_STORAGE_CONTRACT),
                        contractCreate(SIMPLE_STORAGE_CONTRACT).adminKey(THRESHOLD)
                ).when().then(
                        contractCall(SIMPLE_STORAGE_CONTRACT, "setInteger", integerValue
                        )
                                .via("simpleStorageTxn")
                                .gas(100_000L),
                        contractCall(SIMPLE_STORAGE_CONTRACT, "getInteger")
                                .via("simpleStorageTxn")
                                .gas(100_000L),
                        getTxnRecord("simpleStorageTxn").logged()
                );
    }

    private HapiApiSpec successfulOperationsWithLong() {
        final var longValue = 4_294_967_295L;
//        final var longValue = 4_294_967_296L; Invalid long

        return defaultHapiSpec("successfulOperationsWithLong")
                .given(
                        uploadInitCode(SIMPLE_STORAGE_CONTRACT),
                        contractCreate(SIMPLE_STORAGE_CONTRACT).adminKey(THRESHOLD)
                ).when().then(
                        contractCall(SIMPLE_STORAGE_CONTRACT, "setLong", longValue
                        )
                                .via("simpleStorageTxn")
                                .gas(100_000L),
                        contractCall(SIMPLE_STORAGE_CONTRACT, "getLong")
                                .via("simpleStorageTxn")
                                .gas(100_000L),
                        getTxnRecord("simpleStorageTxn").logged()
                );
    }

    private HapiApiSpec successfulOperationsWithBigInteger() {
        final var bigIntegerValue = new BigInteger("9223372036854775809");

        return defaultHapiSpec("successfulOperationsWithBigInteger")
                .given(
                        uploadInitCode(SIMPLE_STORAGE_CONTRACT),
                        contractCreate(SIMPLE_STORAGE_CONTRACT).adminKey(THRESHOLD)
                ).when().then(
                        contractCall(SIMPLE_STORAGE_CONTRACT, "set", bigIntegerValue
                        )
                                .via("simpleStorageTxn")
                                .gas(100_000L),
                        contractCall(SIMPLE_STORAGE_CONTRACT, "get")
                                .via("simpleStorageTxn")
                                .gas(100_000L),
                        getTxnRecord("simpleStorageTxn").logged()
                );
    }

    private HapiApiSpec successfulOperationsWithAddress() {

        return defaultHapiSpec("successfulOperationsWithAddress")
                .given(
                        uploadInitCode(SIMPLE_STORAGE_CONTRACT),
                        contractCreate(SIMPLE_STORAGE_CONTRACT).adminKey(THRESHOLD)
                ).when().then(
                        contractCall(SIMPLE_STORAGE_CONTRACT, "getAddress"
                        )
                                .via("simpleStorageTxn")
                                .gas(100_000L)
                                .hasKnownStatus(SUCCESS),
                        contractCall(SIMPLE_STORAGE_CONTRACT, "setAddress", Address.wrap(Address.toChecksumAddress("0x0000000000000000000000000000000000000000"))
                        )
                                .via("simpleStorageTxn")
                                .gas(100_000L)
                                .hasKnownStatus(SUCCESS),
                        contractCall(SIMPLE_STORAGE_CONTRACT, "setAddress", Address.wrap(Address.toChecksumAddress(BigInteger.valueOf(0x0000000000000000000000000000000000000000)))
                        )
                                .via("simpleStorageTxn")
                                .gas(100_000L)
                                .hasKnownStatus(SUCCESS),
                        getTxnRecord("simpleStorageTxn").logged()
                );
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
