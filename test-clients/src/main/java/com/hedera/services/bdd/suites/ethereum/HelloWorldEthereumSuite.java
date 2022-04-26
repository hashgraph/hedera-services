package com.hedera.services.bdd.suites.ethereum;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.ethereum.EthTxData;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumContractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCodeWithConstructorArguments;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.CONSTRUCTOR;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.apache.commons.lang3.StringUtils.EMPTY;

public class HelloWorldEthereumSuite extends HapiApiSuite {

    private static final Logger log = LogManager.getLogger(HelloWorldEthereumSuite.class);
    private static final long depositAmount = 20_000L;

    private static final String PAY_RECEIVABLE_CONTRACT = "PayReceivable";
    private static final String TOKEN_CREATE_CONTRACT = "TokenCreateContract";
    private static final String OC_TOKEN_CONTRACT = "OcToken";
    private static final String RELAYER = "RELAYER";
    private static final KeyShape secp256k1Shape = KeyShape.SECP256K1;

    public static void main(String... args) {
        new HelloWorldEthereumSuite().runSuiteSync();
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return allOf(
//                ethereumCalls()
                ethereumCreates()
        );
    }

    List<HapiApiSpec> ethereumCalls() {
        return List.of(
                depositSuccess()
        );
    }

    List<HapiApiSpec> ethereumCreates() {
        return List.of(
                smallContractCreate(),
                bigContractCreate(),
                contractCreateWithConstructorArgs()
        );
    }

    HapiApiSpec depositSuccess() {
        final String secp256k1SourceKey = "secp256k1Alias";
        return defaultHapiSpec("DepositSuccess")
                .given(
                        newKeyNamed(secp256k1SourceKey).shape(secp256k1Shape),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, secp256k1SourceKey, ONE_HUNDRED_HBARS)),

                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD)
                ).when(
                        ethereumCall(PAY_RECEIVABLE_CONTRACT, "deposit", depositAmount)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(secp256k1SourceKey)
                                .payingWith(RELAYER)
                                .via("payTxn")
                                .nonce(0)
                                .gas(500_000L)
                                .gasPrice(10L)
                                .maxGasAllowance(5L)
                                .maxPriorityGas(2L)
                                .gasLimit(1_000_000L)
                                .sending(depositAmount)
                                .hasKnownStatus(ResponseCodeEnum.SUCCESS),
                        ethereumCall(PAY_RECEIVABLE_CONTRACT, "deposit", depositAmount)
                                .type(EthTxData.EthTransactionType.LEGACY_ETHEREUM)
                                .signingWith(secp256k1SourceKey)
                                .payingWith(RELAYER)
                                .via("payTxn")
                                .nonce(1)
                                .gas(500_000L)
                                .gasPrice(10L)
                                .maxGasAllowance(5L)
                                .maxPriorityGas(2L)
                                .gasLimit(1_000_000L)
                                .sending(depositAmount)
                                .hasKnownStatus(ResponseCodeEnum.SUCCESS)
                ).then(
                        getTxnRecord("payTxn")
                                .hasPriority(recordWith().contractCallResult(
                                        resultWith().logs(inOrder())))
                );
    }

    HapiApiSpec smallContractCreate() {
        final String secp256k1SourceKey = "secp256k1Alias2";
        return defaultHapiSpec("SmallContractCreate")
                .given(
                        newKeyNamed(secp256k1SourceKey).shape(secp256k1Shape),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, secp256k1SourceKey, ONE_HUNDRED_HBARS)),

                        uploadInitCode(PAY_RECEIVABLE_CONTRACT)
                ).when(
                        ethereumContractCreate(PAY_RECEIVABLE_CONTRACT)
                                .adminKey(THRESHOLD)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(secp256k1SourceKey)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .gas(50_000L)
                                .gasPrice(10L)
                                .maxGasAllowance(5L)
                                .maxPriorityGas(2L)
                                .gasLimit(1_000_000L).hasKnownStatus(SUCCESS)
                ).then(
//                        getAliasedAccountInfo()
                );
    }

    private HapiApiSpec bigContractCreate() {
        final String secp256k1SourceKey = "secp256k1Alias3";
        final var contractAdminKey = "contractAdminKey";
        return defaultHapiSpec("BigContractCreate")
                .given(
                        newKeyNamed(secp256k1SourceKey).shape(secp256k1Shape),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, secp256k1SourceKey, ONE_HUNDRED_HBARS)),
                        newKeyNamed(contractAdminKey),

                        uploadInitCode(TOKEN_CREATE_CONTRACT)
                ).when(
                        ethereumContractCreate(TOKEN_CREATE_CONTRACT)
                                .adminKey(contractAdminKey)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(secp256k1SourceKey)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .gas(50_000L)
                                .gasPrice(10L)
                                .maxGasAllowance(5L)
                                .maxPriorityGas(2L)
                                .gasLimit(1_000_000L)
                                .hasKnownStatus(SUCCESS)
                ).then();
    }

    private HapiApiSpec contractCreateWithConstructorArgs() {
        final String secp256k1SourceKey = "secp256k1Alias4";
        final var contractAdminKey = "contractAdminKey";
        return defaultHapiSpec("ContractCreateWithConstructorArgs")
                .given(
                        newKeyNamed(secp256k1SourceKey).shape(secp256k1Shape),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, secp256k1SourceKey, ONE_HUNDRED_HBARS)),
                        newKeyNamed(contractAdminKey),

                        uploadInitCodeWithConstructorArguments(OC_TOKEN_CONTRACT, getABIFor(CONSTRUCTOR, EMPTY, OC_TOKEN_CONTRACT), 1_000_000L, "OpenCrowd Token", "OCT")
                ).when(
                        ethereumContractCreate(OC_TOKEN_CONTRACT)
                                .adminKey(contractAdminKey)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(secp256k1SourceKey)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .gas(50_000L)
                                .gasPrice(10L)
                                .maxGasAllowance(5L)
                                .maxPriorityGas(2L)
                                .gasLimit(1_000_000L)
                                .hasKnownStatus(SUCCESS)
                ).then();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}