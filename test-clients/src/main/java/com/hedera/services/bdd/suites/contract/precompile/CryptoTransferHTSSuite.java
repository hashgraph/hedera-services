package com.hedera.services.bdd.suites.contract.precompile;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asDotDelimitedLongArray;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CRYPTO_TRANSFER_CONS_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CRYPTO_TRANSFER_FUNGIBLE_TOKENS_LIST;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static java.lang.System.arraycopy;

public class CryptoTransferHTSSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CryptoTransferHTSSuite.class);
	private static final long TOTAL_SUPPLY = 1_000;
	private static final String A_TOKEN = "TokenA";
	private static final String TOKEN_TREASURY = "treasury";

	public static void main(String... args) {
		new CryptoTransferHTSSuite().runSuiteAsync();
	}

	@Override
	public boolean canRunAsync() {
		return true;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveSpecs(),
				negativeSpecs()
		);
	}

	List<HapiApiSpec> negativeSpecs() {
		return List.of();
	}

	List<HapiApiSpec> positiveSpecs() {
		return List.of(
				nonNestedCryptoTransferFungibleTokens()
		);
	}

	private HapiApiSpec nonNestedCryptoTransferFungibleTokens() {
		final var theAccount = "anybody";
		final var cryptoTransferFileByteCode = "cryptoTransferFileByteCode";
//		final var amount = 1_234_567L;
		final var fungibleToken = "fungibleToken";
		final var multiKey = "purpose";
		final var contractKey = "meaning";
		final var theContract = "cryptoTransferContract";
		final var firstCryptoTransferTxn = "firstCryptoTransferTxn";
//		final var contractKeyShape = DELEGATE_CONTRACT;
//		final List<TokenTransferList> tokenTransferLists = getTokenTransferListWithFungibleTokens(new byte[]{},
//				new byte[]{});
//		final var tokenTransferListsAsByte = getTokenTransferListsAsBytes(getTokenTransferListWithFungibleTokens(asAddress(spec.registry().getTokenID(A_TOKEN)),
//				asAddress(spec.registry().getAccountID(theAccount))));
//		final var tokenTrasferListsAsTuple = getTokenTransferListWithFungibleTokensAsTuple()


		final AtomicLong fungibleNum = new AtomicLong();

		return defaultHapiSpec("CryptoTransferFungibleTokensList")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(A_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY),
						fileCreate(cryptoTransferFileByteCode)
								.payingWith(theAccount),
						updateLargeFile(theAccount, cryptoTransferFileByteCode, extractByteCode(ContractResources.CRYPTO_TRANSFER_CONTRACT)),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(0)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.exposingCreatedIdTo(idLit -> fungibleNum.set(asDotDelimitedLongArray(idLit)[2]))
				).when(
						sourcing(() -> contractCreate(theContract, CRYPTO_TRANSFER_CONS_ABI, fungibleNum.get())
								.bytecode(cryptoTransferFileByteCode).payingWith(theAccount)
								.gas(300_000L))
				).then(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(theContract, CRYPTO_TRANSFER_FUNGIBLE_TOKENS_LIST,
														getTokenTransferListWithFungibleTokensAsTuple(asAddress(spec.registry().getTokenID(A_TOKEN)),
																asAddress(spec.registry().getAccountID(theAccount)))).payingWith(theAccount)
														.via(firstCryptoTransferTxn)
														.alsoSigningWithFullPrefix(multiKey))
						),
						getTxnRecord(firstCryptoTransferTxn).andAllChildRecords().logged(),
//						getTokenInfo(fungibleToken).hasTotalSupply(amount),
						getTokenInfo(fungibleToken).logged()
				);
	}

	private Tuple getTokenTransferListWithFungibleTokensAsTuple(final byte[] tokenAddress,
																final byte[] accountAddress) {
		final var token32 = new byte[32];
		arraycopy(tokenAddress, 0, token32, 12, 20);

		final var account32 = new byte[32];
		arraycopy(accountAddress, 0, account32, 12, 20);
		final Tuple accountAmount = new Tuple(account32, 50L);
		final Tuple accountAmount2 = new Tuple(account32, 60L);

		final Tuple nftTransfer = new Tuple(account32, account32, 1L);
		final Tuple nftTransfer2 = new Tuple(account32, account32, 2L);

//		return Tuple.singleton(new Tuple[]{Tuple.of(token32, new Tuple[]{accountAmount, accountAmount2},
//				new Tuple[]{nftTransfer, nftTransfer2})});
		return Tuple.singleton(new Tuple[]{Tuple.of(token32, new Tuple[]{accountAmount, accountAmount2},
				new Tuple[]{})});
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}