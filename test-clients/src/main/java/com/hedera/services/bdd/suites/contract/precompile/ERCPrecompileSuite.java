package com.hedera.services.bdd.suites.contract.precompile;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ERCPrecompileSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ERCPrecompileSuite.class);

	public static void main(String... args) {
		new ERCPrecompileSuite().runSuiteAsync();
	}

	@Override
	public boolean canRunAsync() {
		return true;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				ERC_20(),
				ERC_721()
		);
	}

	List<HapiApiSpec> ERC_20() {
		return List.of(
				balanceOf()
		);
	}

	List<HapiApiSpec> ERC_721() {
		return List.of(
		);
	}

	private HapiApiSpec balanceOf() {
		final var theAccount = "anybody";
		final var fungibleToken = "nonFungibleToken";
		final var multiKey = "purpose";
		final var theContract = "erc20Contract";
		final var balanceTxn = "balanceTxn";

		return defaultHapiSpec("ERC_20_BALANCE_OF")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theAccount).balance(100 * ONE_MILLION_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(5)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey),
						fileCreate(theContract),
						updateLargeFile(theAccount, theContract, extractByteCode(ContractResources.ERC_20_CONTRACT)),
						contractCreate(theContract)
								.bytecode(theContract)
								.gas(300_000)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(theContract,
														ContractResources.ERC_20_BALANCE_OF_CALL,
														asAddress(spec.registry().getTokenID(fungibleToken)),
														asAddress(spec.registry().getAccountID(theAccount)))
														.payingWith(theAccount)
														.via(balanceTxn)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						getTxnRecord(balanceTxn).andAllChildRecords().logged(),
						childRecordsCheck(balanceTxn, SUCCESS,
								recordWith().status(SUCCESS)
//								recordWith().status(SUCCESS).tokenTransfers(NonFungibleTransfers.changingNFTBalances().
//												including(nonFungibleToken, TOKEN_TREASURY, theRecipient, 1))
												)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}