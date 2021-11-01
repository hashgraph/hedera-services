package com.hedera.services.bdd.suites.contract.integration;

import com.google.common.base.Splitter;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.persistence.Contract;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.contract.openzeppelin.ERC1155ContractInteractions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.FACTORY_SELF_DESTRUCT_CONSTRUCTOR_CONTRACT;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractRecords;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileAppend;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_OVERSIZE;

/**
 *  Major todo - find how to use HBars in smart contracts
 */

public class EvmSuites extends HapiApiSuite {
    private final Logger LOGGER = LogManager.getLogger();

    public static void main(String... args) {
        new EvmSuites().runSuiteAsync();
    }

    @Override
    protected List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
//                HSCS_EVM_008_SelfDesctructUpdatesHederaAccount(),
//                HSCS_EVM_0014_ContractCreateFailsOnLargeContracts(),
                HSCS_EVM_005_TransferOfHBarsWorksBetweenContracts()
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

    // Done - for now
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

    // TODO:
    private HapiApiSpec HSCS_EVM_0010_MultiSignatureAccounts() {
        return defaultHapiSpec("HSCS_EVM_009_MinimumChargeOfTxnGasLimit")
                .given()
                .when()
                .then();
    }

    // TODO:
    private HapiApiSpec HSCS_EVM_006_ContractHBarTransferToAccount() {
        return defaultHapiSpec("HSCS_EVM_006_ContractHBarTransferToAccount")
                .given()
                .when()
                .then();
    }

    private HapiApiSpec HSCS_EVM_005_TransferOfHBarsWorksBetweenContracts() {
        final var ACCOUNT = "account";
        final var CONTRACT_FROM = "contract1";
        final var CONTRACT_TO = "contract2";
        return defaultHapiSpec("HSCS_EVM_005_TransferOfHBarsWorksBetweenContracts")
                .given(
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS),

                        fileCreate("contract1Bytecode").path(ContractResources.TRANSFERRING_CONTRACT).payingWith(ACCOUNT),
                        contractCreate(CONTRACT_FROM).bytecode("contract1Bytecode").balance(10_000L).payingWith(ACCOUNT),

                        contractCreate(CONTRACT_TO).bytecode("contract1Bytecode").balance(10_000L).payingWith(ACCOUNT),

                        getContractInfo(CONTRACT_FROM).saveToRegistry("contract_from"),
                        getContractInfo(CONTRACT_TO).saveToRegistry("contract_to"),
                        getAccountInfo(ACCOUNT).savingSnapshot("accountInfo")
                )
                .when(
                        withOpContext((spec, log) -> {
                            var cto = spec.registry().getContractInfo("contract_to").getContractAccountID();

                            var transferCall = contractCall(
                                    CONTRACT_FROM,
                                    ContractResources.TRANSFERRING_CONTRACT_TRANSFERTOADDRESS,
                                    cto, 10)
                                    .payingWith(ACCOUNT).logged();
                            allRunFor(spec, transferCall);
                        })
                )
                .then(
                        getAccountBalance(CONTRACT_FROM).hasTinyBars(10_000 - 10),
                        getAccountBalance(CONTRACT_TO).hasTinyBars(10_000 + 10)
                );
    }

    // TODO:
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
