package com.hedera.services.state.exports;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.ServicesState;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenBalance;
import com.hederahashgraph.api.proto.java.TokenBalances;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.function.UnaryOperator;

import static com.hedera.services.state.exports.SignedStateBalancesExporter.GOOD_SIGNING_ATTEMPT_DEBUG_MSG_TPL;
import static com.hedera.services.state.exports.SignedStateBalancesExporter.b64Encode;
import static com.hedera.services.state.merkle.MerkleEntityAssociation.fromAccountTokenRel;
import static com.hedera.services.state.merkle.MerkleEntityId.fromAccountId;
import static com.hedera.services.state.merkle.MerkleEntityId.fromTokenId;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(JUnitPlatform.class)
class SignedStateBalancesExporterTest {
	FCMap<MerkleEntityId, MerkleToken> tokens = new FCMap<>();
	FCMap<MerkleEntityId, MerkleAccount> accounts = new FCMap<>();
	FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenRels = new FCMap<>();

	Logger mockLog;

	MerkleToken token;
	MerkleToken deletedToken;

	long ledgerFloat = 1_000;

	long thisNodeBalance = 400;
	AccountID thisNode = asAccount("0.0.3");
	long anotherNodeBalance = 100;
	AccountID anotherNode = asAccount("0.0.4");
	long firstNonNodeAccountBalance = 250;
	AccountID firstNonNode = asAccount("0.0.1001");
	long secondNonNodeAccountBalance = 250;
	AccountID secondNonNode = asAccount("0.0.1002");
	AccountID deleted = asAccount("0.0.1003");

	TokenID theToken = asToken("0.0.1004");
	long secondNonNodeTokenBalance = 100;
	TokenID theDeletedToken = asToken("0.0.1005");
	long secondNonNodeDeletedTokenBalance = 100;

	byte[] sig = "not-really-a-sig".getBytes();
	byte[] fileHash = "not-really-a-hash".getBytes();

	MerkleAccount thisNodeAccount, anotherNodeAccount, firstNonNodeAccount, secondNonNodeAccount, deletedAccount;

	GlobalDynamicProperties dynamicProperties = new MockGlobalDynamicProps();

	Instant now = Instant.now();
	Instant shortlyAfter = now.plusSeconds(dynamicProperties.balancesExportPeriodSecs() / 2);
	Instant anEternityLater = now.plusSeconds(dynamicProperties.balancesExportPeriodSecs() * 2);

	ServicesState state;
	PropertySource properties;
	UnaryOperator<byte[]> signer;
	SigFileWriter sigFileWriter;
	FileHashReader hashReader;
	DirectoryAssurance assurance;

	SignedStateBalancesExporter subject;

	@BeforeEach
	public void setUp() throws Exception {
		mockLog = mock(Logger.class);
		given(mockLog.isDebugEnabled()).willReturn(true);
		SignedStateBalancesExporter.log = mockLog;

		thisNodeAccount = MerkleAccountFactory.newAccount().balance(thisNodeBalance).get();
		anotherNodeAccount = MerkleAccountFactory.newAccount().balance(anotherNodeBalance).get();
		firstNonNodeAccount = MerkleAccountFactory.newAccount().balance(firstNonNodeAccountBalance).get();
		secondNonNodeAccount = MerkleAccountFactory.newAccount()
				.balance(secondNonNodeAccountBalance)
				.tokens(theToken, theDeletedToken)
				.get();
		deletedAccount = MerkleAccountFactory.newAccount().deleted(true).get();

		accounts.put(fromAccountId(thisNode), thisNodeAccount);
		accounts.put(fromAccountId(anotherNode), anotherNodeAccount);
		accounts.put(fromAccountId(firstNonNode), firstNonNodeAccount);
		accounts.put(fromAccountId(secondNonNode), secondNonNodeAccount);
		accounts.put(fromAccountId(deleted), deletedAccount);

		token = mock(MerkleToken.class);
		given(token.isDeleted()).willReturn(false);
		deletedToken = mock(MerkleToken.class);
		given(deletedToken.isDeleted()).willReturn(true);
		tokens.put(fromTokenId(theToken), token);
		tokens.put(fromTokenId(theDeletedToken), deletedToken);

		tokenRels.put(
				fromAccountTokenRel(secondNonNode, theToken),
				new MerkleTokenRelStatus(secondNonNodeTokenBalance, false, true));
		tokenRels.put(
				fromAccountTokenRel(secondNonNode, theDeletedToken),
				new MerkleTokenRelStatus(secondNonNodeDeletedTokenBalance, false, true));

		assurance = mock(DirectoryAssurance.class);

		properties = mock(PropertySource.class);
		given(properties.getLongProperty("ledger.totalTinyBarFloat")).willReturn(ledgerFloat);

		var firstNodeAddress = mock(Address.class);
		given(firstNodeAddress.getMemo()).willReturn("0.0.3");
		var secondNodeAddress = mock(Address.class);
		given(secondNodeAddress.getMemo()).willReturn("0.0.4");

		var book = mock(AddressBook.class);
		given(book.getSize()).willReturn(2);
		given(book.getAddress(0)).willReturn(firstNodeAddress);
		given(book.getAddress(1)).willReturn(secondNodeAddress);

		state = mock(ServicesState.class);
		given(state.getNodeAccountId()).willReturn(thisNode);
		given(state.tokens()).willReturn(tokens);
		given(state.accounts()).willReturn(accounts);
		given(state.tokenAssociations()).willReturn(tokenRels);
		given(state.addressBook()).willReturn(book);

		signer = mock(UnaryOperator.class);
		given(signer.apply(fileHash)).willReturn(sig);
		subject = new SignedStateBalancesExporter(properties, signer, dynamicProperties);

		sigFileWriter = mock(SigFileWriter.class);
		hashReader = mock(FileHashReader.class);
		subject.sigFileWriter = sigFileWriter;
		subject.hashReader = hashReader;
	}

	@Test
	public void logsOnIoException() throws IOException {
		// setup:
		var otherDynamicProperties = new MockGlobalDynamicProps() {
			@Override
			public String pathToBalancesExportDir() {
				return "not/a/real/location";
			}
		};
		subject = new SignedStateBalancesExporter(properties, signer, otherDynamicProperties);

		// given:
		subject.directories = assurance;

		// when:
		subject.toCsvFile(state, now);

		// then:
		verify(mockLog).error(any(String.class), any(Throwable.class));
	}

	@Test
	public void logsOnSigningFailure() {
		// setup:
		var loc = testExportLoc();

		given(hashReader.readHash(loc)).willThrow(IllegalStateException.class);

		// when:
		subject.toCsvFile(state, now);

		// then:
		verify(mockLog).error(any(String.class), any(Throwable.class));

		// cleanup:
		new File(loc).delete();
	}

	@Test
	public void usesNewFormatWhenExportingTokenBalances() throws IOException {
		// setup:
		var loc = testExportLoc();

		given(hashReader.readHash(loc)).willReturn(fileHash);
		given(sigFileWriter.writeSigFile(any(), any(), any())).willReturn(loc + "_sig");

		// when:
		subject.toCsvFile(state, now);

		// then:
		var lines = Files.readAllLines(Paths.get(loc));
		var expected = theExpectedBalances();
		assertEquals(expected.size() + 3, lines.size());
		assertEquals(String.format("# " + SignedStateBalancesExporter.CURRENT_VERSION, now), lines.get(0));
		assertEquals(String.format("# TimeStamp:%s", now), lines.get(1));
		assertEquals("shardNum,realmNum,accountNum,balance,tokenBalances", lines.get(2));
		for (int i = 0; i < expected.size(); i++) {
			var entry = expected.get(i);
			assertEquals(String.format(
					"%d,%d,%d,%d,%s",
					entry.getShard(),
					entry.getRealm(),
					entry.getNum(),
					entry.getBalance(),
					entry.getB64TokenBalances()), lines.get(i + 3));
		}
		// and:
		verify(sigFileWriter).writeSigFile(loc, sig, fileHash);
		// and:
		verify(mockLog).debug(String.format(GOOD_SIGNING_ATTEMPT_DEBUG_MSG_TPL, loc + "_sig"));

		// cleanup:
		new File(loc).delete();
	}

	@Test
	public void usesLegacyFormatWhenNotExportingTokenBalances() throws IOException {
		// setup:
		var otherDynamicProperties = new MockGlobalDynamicProps() {
			@Override
			public boolean shouldExportTokenBalances() {
				return false;
			}
		};
		subject = new SignedStateBalancesExporter(properties, signer, otherDynamicProperties);
		subject.sigFileWriter = sigFileWriter;
		subject.hashReader = hashReader;

		// when:
		subject.toCsvFile(state, now);

		// then:
		var lines = Files.readAllLines(Paths.get(testExportLoc()));
		System.out.println(lines);
		var expected = theExpectedBalances();
		System.out.println(expected);
		assertEquals(expected.size() + 2, lines.size());
		assertEquals(String.format("TimeStamp:%s", now), lines.get(0));
		assertEquals("shardNum,realmNum,accountNum,balance", lines.get(1));
		for (int i = 0; i < expected.size(); i++) {
			var entry = expected.get(i);
			assertEquals(String.format(
					"%d,%d,%d,%d",
					entry.getShard(),
					entry.getRealm(),
					entry.getNum(),
					entry.getBalance()), lines.get(i + 2));
		}

		// cleanup:
		new File(testExportLoc()).delete();
	}

	private String testExportLoc() {
		return dynamicProperties.pathToBalancesExportDir()
				+ File.separator
				+ "balance0.0.3"
				+ File.separator
				+ now + "_Balances.csv";
	}

	@Test
	public void summarizesAsExpected() {
		// given:
		List<AccountBalance> expectedBalances = theExpectedBalances();

		// when:
		var summary = subject.summarized(state);

		// then:
		assertEquals(ledgerFloat, summary.getTotalFloat().longValue());
		assertEquals(expectedBalances, summary.getOrderedBalances());
		// and:
		verify(mockLog).warn(String.format(
				SignedStateBalancesExporter.LOW_NODE_BALANCE_WARN_MSG_TPL,
				"0.0.4", anotherNodeBalance));
	}

	private List<AccountBalance> theExpectedBalances() {
		var expThisNode = new AccountBalance(0, 0, 3, thisNodeBalance);
		var expAnotherNode = new AccountBalance(0, 0, 4, anotherNodeBalance);
		var expFirstNon = new AccountBalance(0, 0, 1001, firstNonNodeAccountBalance);
		var expSecondNon = new AccountBalance(0, 0, 1002, secondNonNodeAccountBalance);
		TokenBalances expB64Balances = TokenBalances.newBuilder()
				.addTokenBalances(TokenBalance.newBuilder()
						.setTokenId(theToken)
						.setBalance(secondNonNodeTokenBalance))
				.build();
		expSecondNon.setB64TokenBalances(b64Encode(expB64Balances));
		return List.of(
				expThisNode,
				expAnotherNode,
				expFirstNon,
				expSecondNon
		);
	}

	@Test
	public void assuresExpectedDir() throws IOException {
		// given:
		subject.directories = assurance;

		// when:
		subject.toCsvFile(state, now);

		// then:
		verify(assurance).ensureExistenceOf(expectedExportDir());
	}

	@Test
	public void throwsOnUnexpectedTotalFloat() throws NegativeAccountBalanceException {
		// given:
		anotherNodeAccount.setBalance(anotherNodeBalance + 1);

		// then:
		assertThrows(IllegalStateException.class, () -> subject.toCsvFile(state, now));
	}

	@Test
	public void errorLogsOnIoException() throws IOException {
		// given:
		subject.directories = assurance;
		// and:
		willThrow(IOException.class).given(assurance).ensureExistenceOf(any());

		// when:
		subject.toCsvFile(state, now);

		// then:
		verify(mockLog).error(String.format(
				SignedStateBalancesExporter.BAD_EXPORT_DIR_ERROR_MSG_TPL, expectedExportDir()));
	}

	private String expectedExportDir() {
		return dynamicProperties.pathToBalancesExportDir() + File.separator + "balance0.0.3" + File.separator;
	}

	@Test
	public void initsAsExpected() {
		// expect:
		assertEquals(ledgerFloat, subject.expectedFloat);
		assertEquals(SignedStateBalancesExporter.NEVER, subject.periodEnd);
	}

	@Test
	public void exportsWhenPeriodSecsHaveElapsed() {
		assertFalse(subject.isTimeToExport(now));
		assertEquals(now.plusSeconds(dynamicProperties.balancesExportPeriodSecs()), subject.periodEnd);
		assertFalse(subject.isTimeToExport(shortlyAfter));
		assertEquals(now.plusSeconds(dynamicProperties.balancesExportPeriodSecs()), subject.periodEnd);
		assertTrue(subject.isTimeToExport(anEternityLater));
		assertEquals(anEternityLater.plusSeconds(dynamicProperties.balancesExportPeriodSecs()), subject.periodEnd);
	}

	@AfterAll
	public static void removeDir() {
		new File("src/test/resources/balances0.0.3/").delete();
	}
}