package com.hedera.services.state.exports;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hederahashgraph.api.proto.java.Timestamp;
import com.hedera.services.ServicesState;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hedera.services.stream.proto.AllAccountBalances;
import com.hedera.services.stream.proto.SingleAccountBalances;
import com.hedera.services.stream.proto.TokenUnitBalance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.Units;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.hedera.services.state.merkle.MerkleEntityAssociation.fromAccountTokenRel;
import static com.hedera.services.state.merkle.MerkleEntityId.fromTokenId;
import static com.hedera.services.utils.EntityIdUtils.readableId;

public class SignedStateBalancesExporter implements BalancesExporter {
	static Logger log = LogManager.getLogger(SignedStateBalancesExporter.class);

	static final String LINE_SEPARATOR = System.getProperty("line.separator");
	static final String UNKNOWN_EXPORT_DIR = "";
	static final String BAD_EXPORT_DIR_ERROR_MSG_TPL = "Cannot ensure existence of export dir '%s'!";
	static final String LOW_NODE_BALANCE_WARN_MSG_TPL = "Node '%s' has unacceptably low balance %d!";
	static final String BAD_EXPORT_ATTEMPT_ERROR_MSG_TPL = "Could not export to '%s'!";
	static final String BAD_SIGNING_ATTEMPT_ERROR_MSG_TPL = "Could not sign balance file '%s'!";
	static final String GOOD_SIGNING_ATTEMPT_DEBUG_MSG_TPL = "Created balance signature file '%s'.";
	static final String CURRENT_VERSION = "version:2";

	static final String PROTOBUF_FILE_EXTENSION = ".pb";
	static final String CSV_FILE_EXTENSION = ".csv";

	static final Instant NEVER = null;
	static final Base64.Encoder encoder = Base64.getEncoder();

	final long expectedFloat;
	final UnaryOperator<byte[]> signer;
	final GlobalDynamicProperties dynamicProperties;

	SigFileWriter sigFileWriter = new StandardSigFileWriter();
	FileHashReader hashReader = new Sha384HashReader();
	DirectoryAssurance directories = loc -> Files.createDirectories(Paths.get(loc));

	String lastUsedExportDir = UNKNOWN_EXPORT_DIR;
	Instant periodEnd = NEVER;


	private static final Comparator<AccountID> ACCOUNT_ID_COMPARATOR = Comparator
			.comparingLong(AccountID::getAccountNum)
			.thenComparingLong(AccountID::getShardNum)
			.thenComparingLong(AccountID::getRealmNum);
	private static final Comparator<TokenID> TOKEN_ID_COMPARATOR = Comparator
			.comparingLong(TokenID::getTokenNum)
			.thenComparingLong(TokenID::getRealmNum)
			.thenComparingLong(TokenID::getShardNum);

	private static final Comparator<TokenUnitBalance> TOKEN_UNIT_BALANCE_COMPARATOR = (tb1, tb2) -> {
		int cmp = TOKEN_ID_COMPARATOR.compare(tb1.getTokenId(), tb2.getTokenId());
		if(cmp != 0) {
			return cmp;
		}
		long diff = tb1.getBalance() - tb2.getBalance();
		return diff == 0L ? 0 : (diff < 0 ? -1 : 1);
	};

	/**
	 * Comparator to check whether two SingleAccountBalances instances, including their har and
	 * token balances, are equal or not
	 */
	private static final Comparator<SingleAccountBalances> SINGLE_ACCOUNT_BALANCES_COMPARATOR = (sab1, sab2) -> {
		int result = ACCOUNT_ID_COMPARATOR.compare(sab1.getAccountID(), sab2.getAccountID());
		if (result != 0) {
			return result;
		} else {
			if(sab1.getTokenUnitBalancesList().size() == 0 && sab2.getTokenUnitBalancesList().size() == 0) {
				return 0;
			} else if(sab1.getTokenUnitBalancesList().size() != sab2.getTokenUnitBalancesList().size() ) {
				return -1; // we don't really care which one should be larger
			} else { //   tokenBalances1.size() == tokenBalances2.size(), need to compare tokenBalances one by one
				List<TokenUnitBalance> tokenBalances1 = sab1.getTokenUnitBalancesList()
						.stream().sorted(TOKEN_UNIT_BALANCE_COMPARATOR).collect(Collectors.toList());
				List<TokenUnitBalance> tokenBalances2 = sab2.getTokenUnitBalancesList()
						.stream().sorted(TOKEN_UNIT_BALANCE_COMPARATOR).collect(Collectors.toList());

				Iterator<TokenUnitBalance> it1 = tokenBalances1.listIterator();
				Iterator<TokenUnitBalance> it2 = tokenBalances2.listIterator();
				while(it1.hasNext()) {
					result = TOKEN_UNIT_BALANCE_COMPARATOR.compare(it1.next(), it2.next());
					if(result != 0) {
						return result;
					}
				}
			}
		}
		return 0;
	};

	public SignedStateBalancesExporter(
			PropertySource properties,
			UnaryOperator<byte[]> signer,
			GlobalDynamicProperties dynamicProperties
	) {
		this.signer = signer;
		this.expectedFloat = properties.getLongProperty("ledger.totalTinyBarFloat");
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public boolean isTimeToExport(Instant now) {
		if (periodEnd == NEVER) {
			periodEnd = now.plusSeconds(dynamicProperties.balancesExportPeriodSecs());
		} else {
			if (now.isAfter(periodEnd)) {
				periodEnd = now.plusSeconds(dynamicProperties.balancesExportPeriodSecs());
				return true;
			}
		}
		return false;
	}

	@Override
	public void toCsvFile(ServicesState signedState, Instant exportTimeStamp) {
		if (!ensureExportDir(signedState.getNodeAccountId())) {
			return;
		}
		long startTime = System.nanoTime();
		var summary = summarized(signedState);
		var expected = BigInteger.valueOf(expectedFloat);
		if (!expected.equals(summary.getTotalFloat())) {
			throw new IllegalStateException(String.format(
					"Signed state @ %s had total balance %d not %d!",
					exportTimeStamp,
					summary.getTotalFloat(),
					expectedFloat));
		}
		var csvLoc = lastUsedExportDir
				+ exportTimeStamp.toString().replace(":", "_") + "_Balances" + CSV_FILE_EXTENSION;
		boolean exportSucceeded = exportBalancesFile(summary, csvLoc, exportTimeStamp);
		if (exportSucceeded) {
			tryToSign(csvLoc);
		}
		log.info("It took total {} Millisecond to export and sign the csv account files",
				(System.nanoTime() - startTime) * Units.NANOSECONDS_TO_MILLISECONDS);
	}

	@Override
	public void toProtoFile(ServicesState signedState, Instant exportTimeStamp) {
		if (!ensureExportDir(signedState.getNodeAccountId())) {
			return;
		}

		long startTime = System.nanoTime();
		AllAccountBalances.Builder allAccountBalancesBuilder = AllAccountBalances.newBuilder();

		var expected = BigInteger.valueOf(expectedFloat);
		var total = calcTotalAndBuildProtoMessage(signedState, exportTimeStamp, allAccountBalancesBuilder);

		if (!expected.equals(total)) {
			throw new IllegalStateException(String.format(
					"Signed state @ %s had total balance %d not %d!",
					exportTimeStamp,total,	expectedFloat));
		}

		var protoLoc = lastUsedExportDir
				+ exportTimeStamp.toString().replace(":", "_") + "_Balances" + PROTOBUF_FILE_EXTENSION;
		boolean exportSucceeded = exportBalancesProtoFile(allAccountBalancesBuilder, protoLoc);

		if (exportSucceeded) {
			tryToSign(protoLoc);
		}
		log.info("It took total {} Millisecond to export and sign the proto account files",
				(System.nanoTime() - startTime) * Units.NANOSECONDS_TO_MILLISECONDS);
	}

	private void tryToSign(String csvLoc) {
		try {
			var hash = hashReader.readHash(csvLoc);
			var sig = signer.apply(hash);
			var sigFileLoc = sigFileWriter.writeSigFile(csvLoc, sig, hash);
			if (log.isDebugEnabled()) {
				log.debug(String.format(GOOD_SIGNING_ATTEMPT_DEBUG_MSG_TPL, sigFileLoc));
			}
		} catch (Exception e) {
			log.error(String.format(BAD_SIGNING_ATTEMPT_ERROR_MSG_TPL, csvLoc), e);
		}
	}

	private boolean exportBalancesFile(BalancesSummary summary, String csvLoc, Instant when) {
		try (BufferedWriter fout = Files.newBufferedWriter(Paths.get(csvLoc))) {
			if (dynamicProperties.shouldExportTokenBalances()) {
				addRelease090Header(fout, when);
			} else {
				addLegacyHeader(fout, when);
			}
			for (SingleAccountBalances singleAccountBalances : summary.getOrderedBalances())  {
				fout.write(String.format(
						"%d,%d,%d,%d",
						singleAccountBalances.getAccountID().getShardNum(),
						singleAccountBalances.getAccountID().getRealmNum(),
						singleAccountBalances.getAccountID().getAccountNum(),
						singleAccountBalances.getHbarBalance()));
				if (dynamicProperties.shouldExportTokenBalances()) {
					if(singleAccountBalances.getTokenUnitBalancesList().size() > 0) {
						fout.write("," + b64Encode(singleAccountBalances));
					}
					else {
						fout.write(",");
					}
				}
				fout.write(LINE_SEPARATOR);
			}
		} catch (IOException e) {
			log.error(String.format(BAD_EXPORT_ATTEMPT_ERROR_MSG_TPL, csvLoc), e);
			return false;
		}
		return true;
	}

	private BigInteger calcTotalAndBuildProtoMessage(ServicesState signedState, Instant exportTimeStamp,
			AllAccountBalances.Builder allAccountBalancesBuilder) {

		long nodeBalanceWarnThreshold = dynamicProperties.nodeBalanceWarningThreshold();
		BigInteger totalFloat = BigInteger.valueOf(0L);

		var nodeIds = MiscUtils.getNodeAccounts(signedState.addressBook());
		var tokens = signedState.tokens();
		var accounts = signedState.accounts();
		var tokenAssociations = signedState.tokenAssociations();

		for (MerkleEntityId id : accounts.keySet()) {
			var account = accounts.get(id);
			if (!account.isDeleted()) {
				var accountId = id.toAccountId();
				var balance = account.getBalance();
				if (nodeIds.contains(accountId) && balance < nodeBalanceWarnThreshold) {
					log.warn(String.format(
							LOW_NODE_BALANCE_WARN_MSG_TPL,
							readableId(accountId),
							balance));
				}
				totalFloat = totalFloat.add(BigInteger.valueOf(account.getBalance()));

				SingleAccountBalances.Builder singleAccountBuilder =SingleAccountBalances.newBuilder();
				singleAccountBuilder.setAccountID(accountId)
						.setHbarBalance(balance);

				if (dynamicProperties.shouldExportTokenBalances()) {
					var accountTokens = account.tokens();
					if (accountTokens.numAssociations() > 0) {
						for (TokenID tokenId : accountTokens.asIds()) {
							var token = tokens.get(fromTokenId(tokenId));
							if (token != null && !token.isDeleted()) {
								var relationship = tokenAssociations
										.get(fromAccountTokenRel(accountId, tokenId));
								singleAccountBuilder.addTokenUnitBalances(tb(tokenId, relationship.getBalance()));
							}
						}
					}
				}
				Timestamp.Builder consensusTimeStamp = Timestamp.newBuilder();
				consensusTimeStamp.setSeconds(exportTimeStamp.getEpochSecond())
						.setNanos(exportTimeStamp.getNano());
				allAccountBalancesBuilder.setConsensusTimestamp(consensusTimeStamp.build());
				allAccountBalancesBuilder.addAllAccounts(singleAccountBuilder.build());
			}
		}
		return totalFloat;
	}

	private boolean exportBalancesProtoFile(AllAccountBalances.Builder allAccountsBuilder, String protoLoc) {
		if(log.isDebugEnabled()) {
			log.debug("Export all accounts to protobuf file {} ", protoLoc);
		}

		try (FileOutputStream fout = new FileOutputStream(protoLoc)) {
			allAccountsBuilder.build().writeTo(fout);
		} catch (IOException e) {
			log.error(String.format(BAD_EXPORT_ATTEMPT_ERROR_MSG_TPL, protoLoc), e);
			return false;
		}
		return true;
	}

	public Optional<AllAccountBalances> importBalanceProtoFile(String protoLoc) {
		try {
			FileInputStream fin = new FileInputStream(protoLoc);
			AllAccountBalances allAccountBalances = AllAccountBalances.parseFrom(fin);
			return Optional.ofNullable(allAccountBalances);
		} catch (IOException e) {
			log.error("Can't read protobuf message file {}", protoLoc);
		}
		return Optional.empty();
	}

	private void addLegacyHeader(Writer writer, Instant at) throws IOException {
		writer.write(String.format("TimeStamp:%s%s", at, LINE_SEPARATOR));
		writer.write("shardNum,realmNum,accountNum,balance" + LINE_SEPARATOR);
	}

	private void addRelease090Header(Writer writer, Instant at) throws IOException {
		writer.write("# " + CURRENT_VERSION + LINE_SEPARATOR);
		writer.write(String.format("# TimeStamp:%s%s", at, LINE_SEPARATOR));
		writer.write("shardNum,realmNum,accountNum,balance,tokenBalances" + LINE_SEPARATOR);
	}

	BalancesSummary summarized(ServicesState signedState) {
		long nodeBalanceWarnThreshold = dynamicProperties.nodeBalanceWarningThreshold();
		BigInteger totalFloat = BigInteger.valueOf(0L);
		List<SingleAccountBalances> accountBalances = new ArrayList<>();

		var nodeIds = MiscUtils.getNodeAccounts(signedState.addressBook());
		var tokens = signedState.tokens();
		var accounts = signedState.accounts();
		var tokenAssociations = signedState.tokenAssociations();
		for (MerkleEntityId id : accounts.keySet())	{
			var account = accounts.get(id);
			if (!account.isDeleted()) {
				var accountId = id.toAccountId();
				var balance = account.getBalance();
				if (nodeIds.contains(accountId) && balance < nodeBalanceWarnThreshold) {
					log.warn(String.format(
							LOW_NODE_BALANCE_WARN_MSG_TPL,
							readableId(accountId),
							balance));
				}
				totalFloat = totalFloat.add(BigInteger.valueOf(account.getBalance()));
				SingleAccountBalances.Builder sabBuilder = SingleAccountBalances.newBuilder();
				sabBuilder.setHbarBalance(balance)
						.setAccountID(accountId);
				if (dynamicProperties.shouldExportTokenBalances()) {
					addTokenBalances(accountId, account, sabBuilder,  tokens, tokenAssociations);
				}
				accountBalances.add(sabBuilder.build());
			}
		}
		accountBalances.sort(SINGLE_ACCOUNT_BALANCES_COMPARATOR);
		return new BalancesSummary(totalFloat, accountBalances);
	}

	private void addTokenBalances(
			AccountID id,
			MerkleAccount account,
			SingleAccountBalances.Builder sabBuilder,
			FCMap<MerkleEntityId, MerkleToken> tokens,
			FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenAssociations
	) {
		var accountTokens = account.tokens();
		if (accountTokens.numAssociations() > 0) {

			for (TokenID tokenId : accountTokens.asIds()) {
				var token = tokens.get(fromTokenId(tokenId));
				if (token != null && !token.isDeleted()) {
					var relationship = tokenAssociations.get(fromAccountTokenRel(id, tokenId));
					sabBuilder.addTokenUnitBalances(tb(tokenId, relationship.getBalance()));
				}
			}
		}
	}

	private TokenUnitBalance tb(TokenID id, long balance) {
		return TokenUnitBalance.newBuilder().setTokenId(id).setBalance(balance).build();
	}

	static String b64Encode(SingleAccountBalances accountBalances) {
		return encoder.encodeToString(accountBalances.toByteArray());
	}

	private boolean ensureExportDir(AccountID node) {
		var correctDir = dynamicProperties.pathToBalancesExportDir();
		if (!lastUsedExportDir.startsWith(correctDir)) {
			var sb = new StringBuilder(correctDir);
			if (!correctDir.endsWith(File.separator)) {
				sb.append(File.separator);
			}
			sb.append("balance").append(readableId(node)).append(File.separator);
			var candidateDir = sb.toString();
			try {
				directories.ensureExistenceOf(candidateDir);
				lastUsedExportDir = candidateDir;
			} catch (IOException e) {
				log.error(String.format(BAD_EXPORT_DIR_ERROR_MSG_TPL, candidateDir));
				return false;
			}
		}
		return true;
	}

	static class BalancesSummary {
		private final BigInteger totalFloat;
		private final List<SingleAccountBalances> orderedBalances;

		BalancesSummary(
				BigInteger totalFloat,
				List<SingleAccountBalances> orderedBalances
		) {
			this.totalFloat = totalFloat;
			this.orderedBalances = orderedBalances;
		}

		public BigInteger getTotalFloat() {
			return totalFloat;
		}

		public List<SingleAccountBalances> getOrderedBalances() {
			return orderedBalances;
		}
	}
}

