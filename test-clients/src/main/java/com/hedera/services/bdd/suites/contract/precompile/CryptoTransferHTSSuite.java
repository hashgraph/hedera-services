package com.hedera.services.bdd.suites.contract.precompile;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CRYPTO_TRANSFER_CONS_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CRYPTO_TRANSFER_FUNGIBLE_TOKENS_LIST;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
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
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static java.lang.System.arraycopy;

public class CryptoTransferHTSSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CryptoTransferHTSSuite.class);
	private static final long TOTAL_SUPPLY = 1_000;
	private static final String FUNGIBLE_TOKEN = "TokenA";
	private static final String NFT_TOKEN = "Token_NFT";
	private static final String TOKEN_TREASURY = "treasury";
	private static final String RECEIVER = "receiver";
	private static final String ACCOUNT = "anybody";

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
				nonNestedCryptoTransferFungibleTokens(),
				nonNestedCryptoTransferNonFungibleTokens()
		);
	}

	private HapiApiSpec nonNestedCryptoTransferFungibleTokens() {
		final var cryptoTransferFileByteCode = "cryptoTransferFileByteCode";
		final var multiKey = "purpose";
		final var theContract = "cryptoTransferContract";
		final var firstCryptoTransferTxn = "firstCryptoTransferTxn";

		final AtomicLong fungibleNum = new AtomicLong();

		return defaultHapiSpec("CryptoTransferFungibleTokensList")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(ACCOUNT).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECEIVER).balance(2 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.treasury(TOKEN_TREASURY),
						fileCreate(cryptoTransferFileByteCode)
								.payingWith(ACCOUNT),
						updateLargeFile(ACCOUNT, cryptoTransferFileByteCode,
								extractByteCode(ContractResources.CRYPTO_TRANSFER_CONTRACT)),
						tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(RECEIVER, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(200, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ACCOUNT)).payingWith(ACCOUNT)
				).when(
						sourcing(() -> contractCreate(theContract, CRYPTO_TRANSFER_CONS_ABI, fungibleNum.get())
								.bytecode(cryptoTransferFileByteCode).payingWith(ACCOUNT)
								.gas(300_000L))
				).then(
						withOpContext(
								(spec, opLog) ->
								{
									final var token = asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN));
									final var sender = asAddress(spec.registry().getAccountID(ACCOUNT));
									final var receiver = asAddress(spec.registry().getAccountID(RECEIVER));
									final var amountToBeSent = 50L;
									allRunFor(
											spec,
											contractCall(theContract, CRYPTO_TRANSFER_FUNGIBLE_TOKENS_LIST,
													getTokenTransferListWithFungibleTokensAsTuple(token,
															sender,
															receiver, amountToBeSent)).payingWith(ACCOUNT)
													.via(firstCryptoTransferTxn)
													.alsoSigningWithFullPrefix(multiKey));
								}),
						getTxnRecord(firstCryptoTransferTxn).andAllChildRecords().logged(),
						getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(TOTAL_SUPPLY),
						getTokenInfo(FUNGIBLE_TOKEN).logged()
				);
	}

	private HapiApiSpec nonNestedCryptoTransferNonFungibleTokens() {
		final var cryptoTransferFileByteCode = "cryptoTransferFileByteCode";
		final var multiKey = "purpose";
		final var theContract = "cryptoTransferContract";
		final var firstCryptoTransferTxn = "firstCryptoTransferTxn";

		final AtomicLong nonFungibleNum = new AtomicLong();

		return defaultHapiSpec("CryptoTransferNonFungibleTokensList")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(ACCOUNT).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECEIVER),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY),
						fileCreate(cryptoTransferFileByteCode)
								.payingWith(ACCOUNT),
						updateLargeFile(ACCOUNT, cryptoTransferFileByteCode,
								extractByteCode(ContractResources.CRYPTO_TRANSFER_CONTRACT)),
						tokenAssociate(ACCOUNT, List.of(NFT_TOKEN)),
						mintToken(NFT_TOKEN, List.of(metadata("firstMemo"), metadata("secondMemo"))),
						tokenAssociate(RECEIVER, List.of(NFT_TOKEN)),
						cryptoTransfer(TokenMovement.movingUnique(NFT_TOKEN, 1).between(TOKEN_TREASURY, ACCOUNT)).payingWith(ACCOUNT)
				).when(
						sourcing(() -> contractCreate(theContract, CRYPTO_TRANSFER_CONS_ABI, nonFungibleNum.get())
								.bytecode(cryptoTransferFileByteCode).payingWith(ACCOUNT)
								.gas(300_000L))
				).then(
						withOpContext(
								(spec, opLog) ->
								{
									final var token = asAddress(spec.registry().getTokenID(NFT_TOKEN));
									final var sender = asAddress(spec.registry().getAccountID(ACCOUNT));
									final var receiver = asAddress(spec.registry().getAccountID(RECEIVER));
									allRunFor(
											spec,
											contractCall(theContract, CRYPTO_TRANSFER_FUNGIBLE_TOKENS_LIST,
													getTokenTransferListWithNonFungibleTokensAsTuple(token,
															sender,
															receiver, 1L)).payingWith(ACCOUNT)
													.via(firstCryptoTransferTxn)
													.alsoSigningWithFullPrefix(multiKey));
								}),
						getTxnRecord(firstCryptoTransferTxn).andAllChildRecords().logged(),
						getTokenInfo(NFT_TOKEN).hasTotalSupply(2),
						getAccountInfo(RECEIVER).hasOwnedNfts(1),
						getAccountBalance(RECEIVER).hasTokenBalance(NFT_TOKEN, 1),
						getAccountInfo(ACCOUNT).hasOwnedNfts(0),
						getAccountBalance(ACCOUNT).hasTokenBalance(NFT_TOKEN, 0),
						getTokenInfo(NFT_TOKEN).logged()
				);
	}

	private Tuple getTokenTransferListWithFungibleTokensAsTuple(final byte[] tokenAddress,
																final byte[] accountAddress,
																final byte[] receiverAddress,
																final long amountToBeSent) {
		final var token32 = getAddressWithFilledEmptyBytes(tokenAddress);
		final var  accountAmounts = new Tuple[] {getAccountAmountTuple(accountAddress, -amountToBeSent),
				getAccountAmountTuple(receiverAddress, amountToBeSent)} ;

		return Tuple.singleton(new Tuple[]{Tuple.of(token32, accountAmounts,
				new Tuple[]{})});
	}

	private Tuple getTokenTransferListWithNonFungibleTokensAsTuple(final byte[] tokenAddress,
																   final byte[] accountAddress,
																   final byte[] receiverAddress,
																   final long serialNumber) {
		final var token32 = getAddressWithFilledEmptyBytes(tokenAddress);
		final var nftTransfers = new Tuple[]{getNftTransferTuple(accountAddress, receiverAddress, serialNumber)};

		return Tuple.singleton(new Tuple[]{Tuple.of(token32, new Tuple[]{},
				nftTransfers)});
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