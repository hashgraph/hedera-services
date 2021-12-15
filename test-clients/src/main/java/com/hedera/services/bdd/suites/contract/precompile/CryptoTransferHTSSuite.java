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
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CRYPTO_TRANSFER_CONS_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CRYPTO_TRANSFER_FUNGIBLE_TOKENS_LIST;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
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

//	private HapiApiSpec nonNestedCryptoTransferFungibleTokens() {
//		final var theAccount = "anybody";
//		final var theReceiver = "receiver";
//		final var cryptoTransferFileByteCode = "cryptoTransferFileByteCode";
////		final var amount = 1_234_567L;
//		final var fungibleToken = "fungibleToken";
//		final var multiKey = "purpose";
//		final var contractKey = "meaning";
//		final var theContract = "cryptoTransferContract";
//		final var creationTx = "creationTx";
//		final var firstCryptoTransferTxn = "firstCryptoTransferTxn";
////		final var contractKeyShape = DELEGATE_CONTRACT;
////		final List<TokenTransferList> tokenTransferLists = getTokenTransferListWithFungibleTokens(new byte[]{},
////				new byte[]{});
////		final var tokenTransferListsAsByte = getTokenTransferListsAsBytes(getTokenTransferListWithFungibleTokens(asAddress(spec.registry().getTokenID(A_TOKEN)),
////				asAddress(spec.registry().getAccountID(theAccount))));
////		final var tokenTrasferListsAsTuple = getTokenTransferListWithFungibleTokensAsTuple()
//
//
//		final AtomicLong fungibleNum = new AtomicLong();
//
//		return defaultHapiSpec("CryptoTransferFungibleTokensList")
//				.given(
//						newKeyNamed(multiKey),
//						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
//						cryptoCreate(theReceiver),
//						cryptoCreate(TOKEN_TREASURY),
//						tokenCreate(A_TOKEN)
//								.tokenType(TokenType.FUNGIBLE_COMMON)
//								.initialSupply(TOTAL_SUPPLY)
//								.treasury(TOKEN_TREASURY),
//						fileCreate(cryptoTransferFileByteCode)
//								.payingWith(theAccount),
//						updateLargeFile(theAccount, cryptoTransferFileByteCode, extractByteCode(ContractResources.CRYPTO_TRANSFER_CONTRACT)),
////						withOpContext(
////								(spec, opLog) ->
////										allRunFor(
////												spec,
////												contractCreate(theContract)
////														.payingWith(theAccount)
////														.bytecode(ContractResources.CRYPTO_TRANSFER_CONTRACT)
////														.via(creationTx)
////														.gas(380_000))),
//						contractCreate(theContract)
//								.payingWith(theAccount)
//								.bytecode(cryptoTransferFileByteCode)
//								.via(creationTx)
//								.gas(380_000),
//						getTxnRecord("creationTx").logged(),
//						tokenAssociate(theAccount, List.of(A_TOKEN)),
//						tokenAssociate(theContract, List.of(A_TOKEN)),
//						tokenAssociate(theReceiver, List.of(A_TOKEN)),
//						cryptoTransfer(moving(200, A_TOKEN).between(TOKEN_TREASURY, theAccount))
//				).when(
//						withOpContext(
//								(spec, opLog) -> {
//									final var token = asAddress(spec.registry().getTokenID(A_TOKEN));
//									final var sender = asAddress(spec.registry().getAccountID(theAccount));
//									final var receiver1 = asAddress(spec.registry().getAccountID(theReceiver));
//									//		final var receiver2 = asAddress(spec.registry().getAccountID(theSecondReceiver));
//									final var accounts = List.of(sender, receiver1);
//									final var amounts = List.of(-10L, 5L);
//
//									allRunFor(
//											spec,
//											contractCall(theContract, CRYPTO_TRANSFER_FUNGIBLE_TOKENS_LIST,
//													getTokenTransferListWithFungibleTokensAsTuple(token,
//													sender,receiver1)).payingWith(theAccount)
//													.via(firstCryptoTransferTxn)
//													.alsoSigningWithFullPrefix(multiKey));
//								}
//						),
//						getTxnRecord(firstCryptoTransferTxn).andAllChildRecords().logged(),
////						getTokenInfo(fungibleToken).hasTotalSupply(amount),
//						getTokenInfo(fungibleToken).logged()
//				).then();
//	}

	private HapiApiSpec nonNestedCryptoTransferFungibleTokens() {
		final var theAccount = "anybody";
		final var theReceiver = "receiver";
		final var cryptoTransferFileByteCode = "cryptoTransferFileByteCode";
//		final var amount = 1_234_567L;
		final var fungibleToken = "fungibleToken";
		final var multiKey = "purpose";
		final var contractKey = "meaning";
		final var theContract = "cryptoTransferContract";
		final var firstCryptoTransferTxn = "firstCryptoTransferTxn";

		final AtomicLong fungibleNum = new AtomicLong();

		return defaultHapiSpec("CryptoTransferFungibleTokensList")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(theReceiver).balance(2 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(A_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.treasury(TOKEN_TREASURY),
						fileCreate(cryptoTransferFileByteCode)
								.payingWith(theAccount),
						updateLargeFile(theAccount, cryptoTransferFileByteCode,
								extractByteCode(ContractResources.CRYPTO_TRANSFER_CONTRACT)),
						tokenAssociate(theAccount, List.of(A_TOKEN)),
//						tokenAssociate(theContract, List.of(A_TOKEN)),
						tokenAssociate(theReceiver, List.of(A_TOKEN)),
						cryptoTransfer(moving(200, A_TOKEN).between(TOKEN_TREASURY, theAccount)).payingWith(theAccount)
				).when(
						sourcing(() -> contractCreate(theContract, CRYPTO_TRANSFER_CONS_ABI, fungibleNum.get())
								.bytecode(cryptoTransferFileByteCode).payingWith(theAccount)
								.gas(300_000L))
				).then(
						withOpContext(
								(spec, opLog) ->
								{
									final var token = asAddress(spec.registry().getTokenID(A_TOKEN));
									final var sender = asAddress(spec.registry().getAccountID(theAccount));
									final var receiver1 = asAddress(spec.registry().getAccountID(theReceiver));
									allRunFor(
											spec,
											contractCall(theContract, CRYPTO_TRANSFER_FUNGIBLE_TOKENS_LIST,
													getTokenTransferListWithFungibleTokensAsTuple(token,
															sender,
															receiver1)).payingWith(theAccount)
													.via(firstCryptoTransferTxn)
													.alsoSigningWithFullPrefix(multiKey));
								}),
						getTxnRecord(firstCryptoTransferTxn).andAllChildRecords().logged(),
						getTokenInfo(A_TOKEN).hasTotalSupply(TOTAL_SUPPLY),
						getTokenInfo(A_TOKEN).logged()
				);
	}

	private Tuple getTokenTransferListWithFungibleTokensAsTuple(final byte[] tokenAddress,
																final byte[] accountAddress,
																final byte[] receiverAddress) {
		final var token32 = getAddressWithFilledEmptyBytes(tokenAddress);
		final var  accountAmounts = new Tuple[] {getAccountAmountTuple(accountAddress, -50L),
				getAccountAmountTuple(receiverAddress, 50L)} ;
		final Tuple nftTransfer = new Tuple(accountAddress, accountAddress, 1L);
//		final Tuple nftTransfer2 = new Tuple(account32, account32, 2L);

		return Tuple.singleton(new Tuple[]{Tuple.of(token32, accountAmounts,
				new Tuple[]{})});
	}

	private Tuple getAccountAmountTuple(final byte[] account, final long amount) {
		return new Tuple(getAddressWithFilledEmptyBytes(account), amount);
	}

	private Tuple getNftTransferTuple(final byte[] sender, final byte[] receiver, final long serialNumber) {
		return new Tuple(getAddressWithFilledEmptyBytes(sender), getAddressWithFilledEmptyBytes(receiver),
				serialNumber);
	}

	private byte[] getAddressWithFilledEmptyBytes(final byte[] address20) {
		final var address32 = new byte[32];
		arraycopy(address20, 0, address32, 12, 20);
		return address32;
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}