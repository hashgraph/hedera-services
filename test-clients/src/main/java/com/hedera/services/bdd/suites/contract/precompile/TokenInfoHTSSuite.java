package com.hedera.services.bdd.suites.contract.precompile;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.swirlds.common.utility.CommonUtils.hex;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenInfoHTSSuite extends HapiApiSuite {

    private static final Logger LOG = LogManager.getLogger(TokenInfoHTSSuite.class);

    private static final String TOKEN_INFO_CONTRACT = "TokenInfoContract";

    public static void main(String... args) {
        new TokenInfoHTSSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<HapiApiSpec> negativeSpecs() {
        return List.of();
    }

    List<HapiApiSpec> positiveSpecs() {
        return List.of(happyPathGetTokenInfo());
    }

    private HapiApiSpec happyPathGetTokenInfo() {
        final String TOKEN_INFO_TXN = "TokenInfoTxn";
        final String memo = "JUMP";
        final String name = "primary";
        return defaultHapiSpec("HappyPathGetTokenInfo")
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate("autoRenewAccount").balance(0L),
                        newKeyNamed("adminKey"),
                        newKeyNamed("freezeKey"),
                        newKeyNamed("kycKey"),
                        newKeyNamed("supplyKey"),
                        newKeyNamed("wipeKey"),
                        newKeyNamed("feeScheduleKey"),
                        newKeyNamed("pauseKey"),
                        uploadInitCode(TOKEN_INFO_CONTRACT),
                        contractCreate(TOKEN_INFO_CONTRACT).gas(1_000_000L),
                        //            .adminKey(contractAdminKey)
                        //            .autoRenewAccountId(AUTO_RENEW_ACCOUNT)
                        //            .signedBy(contractAdminKey, DEFAULT_PAYER, AUTO_RENEW_ACCOUNT)
                        tokenCreate("primary")
                                .supplyType(TokenSupplyType.FINITE)
                                .entityMemo(memo)
                                .name(name)
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount("autoRenewAccount")
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .maxSupply(1000)
                                .initialSupply(500)
                                .decimals(1)
                                .adminKey("adminKey")
                                .freezeKey("freezeKey")
                                .kycKey("kycKey")
                                .supplyKey("supplyKey")
                                .wipeKey("wipeKey")
                                .feeScheduleKey("feeScheduleKey")
                                .pauseKey("pauseKey")
                                .via("createTxn"))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TOKEN_INFO_CONTRACT,
                                                                "getInformationForToken",
                                                    Tuple.of(expandByteArrayTo32Length(asAddress(
                                                                        spec.registry()
                                                                                .getTokenID(name)))))
                                                        .via(TOKEN_INFO_TXN)
                                                        .gas(1_000_000L)
                                                //                        .exposingResultTo(result
                                                // -> {
                                                //                          LOG.info("Explicit token
                                                // info is {}",
                                                // result[0]);
                                                //                          final var res = (byte[])
                                                // result[0];
                                                //                          createTokenNum.set(new
                                                // BigInteger(res).longValueExact());
                                                //                        })
                                                )))
                .then(getTxnRecord(TOKEN_INFO_TXN).andAllChildRecords().logged());
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }

    private static Address asHeadlongAddress(final byte[] solidityAddress) {
        final var result = hex(solidityAddress);
        final var prefix = "0x";
        var headlongAdds = prefix + result;
        return Address.wrap(Address.toChecksumAddress(headlongAdds));
    }

    private static byte[] expandByteArrayTo32Length(final byte[] bytesToExpand) {
        byte[] expandedArray = new byte[32];

        System.arraycopy(
            bytesToExpand,
            0,
            expandedArray,
            expandedArray.length - bytesToExpand.length,
            bytesToExpand.length);
        return expandedArray;
    }
}
