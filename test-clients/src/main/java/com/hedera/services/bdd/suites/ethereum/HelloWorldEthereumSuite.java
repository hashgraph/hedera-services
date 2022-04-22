package com.hedera.services.bdd.suites.ethereum;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.keys.KeyLabel;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.utilops.EthTxData;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;

import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

public class HelloWorldEthereumSuite extends HapiApiSuite {

    private static final Logger log = LogManager.getLogger(HelloWorldEthereumSuite.class);
    private static final long depositAmount = 20_000_000_000L;

    private static final String PAY_RECEIVABLE_CONTRACT = "PayReceivable";

    public static void main(String... args) {
        new HelloWorldEthereumSuite().runSuiteSync();
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(depositSuccess());
    }

    HapiApiSpec depositSuccess() {
        final var ACCOUNT = "ETH_SIGNER";
        final var secp256k1SourceKey = "secp256k1Alias";
        final var secp256k1Shape = KeyShape.SECP256K1;

        return defaultHapiSpec("DepositSuccess")
                .given(
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD),

                        newKeyNamed(secp256k1SourceKey).shape(secp256k1Shape).labels(KeyLabel.simple("MyKey")),
                        cryptoCreate(ACCOUNT).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, secp256k1SourceKey, ONE_HUNDRED_HBARS))
                ).when(
                        ethereumCall(PAY_RECEIVABLE_CONTRACT, "deposit", depositAmount)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(secp256k1SourceKey)
                                .payingWith(ACCOUNT)
                                .via("payTxn")
                                .gas(500_000L)
                                .gasPrice(10L)
                                .maxGasAllowance(5L)
                                .maxPriorityGas(2L)
                                .gasLimit(1_000_000L)
                                .sending(depositAmount)
                ).then(
                        getTxnRecord("payTxn")
                                .hasPriority(recordWith().contractCallResult(
                                        resultWith().logs(inOrder())))
                );
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
