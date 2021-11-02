package com.hedera.services.bdd.suites.contract.integration;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

/**
 * Major todo - find how to use HBars in smart contracts
 */

public class EvmSuites extends HapiApiSuite {
    private final Logger LOGGER = LogManager.getLogger();

    public static void main(String... args) {
        new EvmSuites().runSuiteAsync();
    }

    @Override
    protected List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                HSCS_EVM_005_TransferOfHBarsWorksBetweenContracts(),
                HSCS_EVM_006_ContractHBarTransferToAccount(),
                HSCS_EVM_005_TransfersWithSubLevelCallsBetweenContracts()
        );
    }

    private HapiApiSpec HSCS_EVM_006_ContractHBarTransferToAccount() {
        final var ACCOUNT = "account";
        final var CONTRACT_FROM = "contract1";
        return defaultHapiSpec("HSCS_EVM_006_ContractHBarTransferToAccount")
                .given(
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS),
                        cryptoCreate("receiver").balance(10_000L),

                        fileCreate("contract1Bytecode").path(ContractResources.TRANSFERRING_CONTRACT).payingWith(ACCOUNT),
                        contractCreate(CONTRACT_FROM).bytecode("contract1Bytecode").balance(10_000L).payingWith(ACCOUNT),

                        getContractInfo(CONTRACT_FROM).saveToRegistry("contract_from"),
                        getAccountInfo(ACCOUNT).savingSnapshot("accountInfo"),
                        getAccountInfo("receiver").savingSnapshot("receiverInfo")
                )
                .when(
                        withOpContext((spec, log) -> {
                            var receiverAddr = spec.registry().getAccountInfo("receiverInfo").getContractAccountID();
                            var transferCall = contractCall(
                                    CONTRACT_FROM,
                                    ContractResources.TRANSFERRING_CONTRACT_TRANSFERTOADDRESS,
                                    receiverAddr, 10)
                                    .payingWith(ACCOUNT).logged();
                            allRunFor(spec, transferCall);
                        })
                )
                .then(
                        getAccountBalance("receiver").hasTinyBars(10_000 + 10)
                );
    }

    private HapiApiSpec HSCS_EVM_005_TransfersWithSubLevelCallsBetweenContracts() {
        final var ACCOUNT = "account";
        final var TOP_LEVEL_CONTRACT = "tlc";
        final var SUB_LEVEL_CONTRACT = "slc";
        final var INITIAL_CONTRACT_BALANCE = 100;

        return defaultHapiSpec("HSCS_EVM_005_TransfersWithSubLevelCallsBetweenContracts")
                .given(
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS),
                        fileCreate(TOP_LEVEL_CONTRACT + "bytecode").path(ContractResources.TOP_LEVEL_TRANSFERRING_CONTRACT),
                        fileCreate(SUB_LEVEL_CONTRACT + "bytecode").path(ContractResources.SUB_LEVEL_TRANSFERRING_CONTRACT)
                )
                .when(
                        contractCreate(TOP_LEVEL_CONTRACT).bytecode(TOP_LEVEL_CONTRACT + "bytecode").payingWith(ACCOUNT).balance(INITIAL_CONTRACT_BALANCE),
                        contractCreate(SUB_LEVEL_CONTRACT).bytecode(SUB_LEVEL_CONTRACT + "bytecode").payingWith(ACCOUNT).balance(INITIAL_CONTRACT_BALANCE)
                )
                .then(
                        contractCall(TOP_LEVEL_CONTRACT).sending(10).payingWith(ACCOUNT),
                        getAccountBalance(TOP_LEVEL_CONTRACT).hasTinyBars(INITIAL_CONTRACT_BALANCE + 10),

                        contractCall(TOP_LEVEL_CONTRACT, ContractResources.TOP_LEVEL_TRANSFERRING_CONTRACT_TRANSFER_CALL_PAYABLE_ABI)
                                .sending(10)
                                .payingWith(ACCOUNT),
                        getAccountBalance(TOP_LEVEL_CONTRACT).hasTinyBars(INITIAL_CONTRACT_BALANCE + 20),

                        contractCall(TOP_LEVEL_CONTRACT, ContractResources.TOP_LEVEL_TRANSFERRING_CONTRACT_NON_PAYABLE_ABI)
                                .sending(10)
                                .payingWith(ACCOUNT)
                                .hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED),
                        getAccountBalance(TOP_LEVEL_CONTRACT).hasTinyBars(INITIAL_CONTRACT_BALANCE + 20),

                        getContractInfo(TOP_LEVEL_CONTRACT).saveToRegistry("tcinfo"),

                        /* sub-level non-payable contract call */
                        assertionsHold((spec, log) -> {
                            final var subLevelSolidityAddr = spec.registry().getContractInfo("tcinfo").getContractAccountID();
                            final var cc = contractCall(SUB_LEVEL_CONTRACT, ContractResources.SUB_LEVEL_NON_PAYABLE_ABI, subLevelSolidityAddr, 20L)
                                    .hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED);
                            allRunFor(spec, cc);
                        }),
                        getAccountBalance(TOP_LEVEL_CONTRACT).hasTinyBars(20 + INITIAL_CONTRACT_BALANCE),
                        getAccountBalance(SUB_LEVEL_CONTRACT).hasTinyBars(INITIAL_CONTRACT_BALANCE),

                        /* sub-level payable contract call */
                        assertionsHold((spec, log) -> {
                            final var subLevelSolidityAddr = spec.registry().getContractInfo("tcinfo").getContractAccountID();
                            // FIXME: this is currently failing, and needs investigation
                            final var cc = contractCall(SUB_LEVEL_CONTRACT, ContractResources.SUB_LEVEL_PAYABLE_ABI, subLevelSolidityAddr, 20L)
                                    .sending(20);
                            allRunFor(spec, cc);
                        }),
                        getAccountBalance(TOP_LEVEL_CONTRACT).hasTinyBars(INITIAL_CONTRACT_BALANCE),
                        getAccountBalance(SUB_LEVEL_CONTRACT).hasTinyBars(20 + INITIAL_CONTRACT_BALANCE)

                );
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

    @Override
    protected Logger getResultsLogger() {
        return LOGGER;
    }
}
