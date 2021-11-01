package com.hedera.services.bdd.suites.contract.integration;

import com.google.common.base.Splitter;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.contract.openzeppelin.ERC1155ContractInteractions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.FACTORY_SELF_DESTRUCT_CONSTRUCTOR_CONTRACT;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileAppend;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_OVERSIZE;

public class EvmSuites extends HapiApiSuite {
    private final Logger LOGGER = LogManager.getLogger();

    public static void main(String... args) {
        new EvmSuites().runSuiteAsync();
    }

    @Override
    protected List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
//                HSCS_EVM_008_SelfDesctructUpdatesHederaAccount(),
                HSCS_EVM_0014_ContractCreateFailsOnLargeContracts()
        );
    }

    // Done
    private HapiApiSpec HSCS_EVM_008_SelfDesctructUpdatesHederaAccount() {
        return defaultHapiSpec("HSCS_EVM_008_SelfDestructUpdatesHederaAccount")
                .given(
                        fileCreate("bytecode")
                                .path(FACTORY_SELF_DESTRUCT_CONSTRUCTOR_CONTRACT)
                )
                .when(
                        contractCreate("selfDestroying")
                                .bytecode("bytecode")
                                .via("contractCreate")
                                .hasKnownStatus(SUCCESS)
                )
                .then(
                        getAccountInfo("selfDestroying")
                                .hasCostAnswerPrecheck(ACCOUNT_DELETED),
                        getContractInfo("selfDestroying")
                                .hasCostAnswerPrecheck(CONTRACT_DELETED)
                );
    }

    // Validates that ContractCreate fails to create a contract > 24KiB
    // By uploading a LARGE file with multiple file appends, it is possible
    // However, trying to do it at 1 tx will result in TRANSACTION_OVERSIZE
    private HapiApiSpec HSCS_EVM_0014_ContractCreateFailsOnLargeContracts() {
        final var PAYER = "payer";
        final var data = ERC1155ContractInteractions.getFileContents(ContractResources.LARGE_CONTRACT);
        return defaultHapiSpec("HSCS_EVM_0014_ContractCreateFailsOnLargeContracts")
                .given(
                        cryptoCreate(PAYER)
                                .balance(ONE_MILLION_HBARS),
                        fileCreate("oversized").path(ContractResources.LARGE_CONTRACT).hasPrecheck(TRANSACTION_OVERSIZE),
                        fileCreate("bytecode")
                                .contents("")
                                .payingWith(PAYER),
                        withOpContext((spec, log) -> {
                            var stringIterable = Splitter.fixedLength(4096).split(data.toStringUtf8());
                            for (var subs : stringIterable) {
                                var fap = fileAppend("bytecode").content(subs).payingWith(PAYER);
                                allRunFor(spec, fap);
                            }
                        })
                )
                .when(
                        contractCreate("contract")
                                .bytecode("bytecode").via("cc")
                                .payingWith(PAYER)
                                .hasPrecheck(OK).hasAnyKnownStatus(),

                        getTxnRecord("cc").logged()
                )
                .then();
    }

    private HapiApiSpec HSCS_EVM_0010_MultiSignatureAccounts() {
        return defaultHapiSpec("HSCS_EVM_009_MinimumChargeOfTxnGasLimit")
                .given()
                .when()
                .then();
    }

    private HapiApiSpec HSCS_EVM_006_ContractHBarTransferToAccount() {
        return defaultHapiSpec("HSCS_EVM_006_ContractHBarTransferToAccount")
                .given()
                .when()
                .then();
    }

    private HapiApiSpec HSCS_EVM_005_TransferOfHBarsWorksBetweenContracts() {
        return defaultHapiSpec("HSCS_EVM_005_TransferOfHBarsWorksBetweenContracts")
                .given()
                .when()
                .then();
    }

    private HapiApiSpec HSCS_EVM_004_GasRefundingLogicWorksAsExpected() {
        return defaultHapiSpec("HSCS_EVM_004_GasRefundingLogic")
                .given()
                .when()
                .then();
    }


    @Override
    protected Logger getResultsLogger() {
        return LOGGER;
    }
}
