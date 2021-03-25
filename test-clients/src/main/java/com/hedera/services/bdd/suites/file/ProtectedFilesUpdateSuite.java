package com.hedera.services.bdd.suites.file;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.queries.file.HapiGetFileContents;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.sysfiles.AddressBookPojo;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.AddressBook;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.UnaryOperator;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

public class ProtectedFilesUpdateSuite extends HapiApiSuite {
	private final ObjectMapper mapper = new ObjectMapper();
	private static final Logger log = LogManager.getLogger(ProtectedFilesUpdateSuite.class);

	// The number of chars that separate a property and its value
	private static final int PROPERTY_VALUE_SPACE_LENGTH = 2;

	public static void main(String... args) {
		new ProtectedFilesUpdateSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveTests(),
				negativeTests()
		);
	}

	private List<HapiApiSpec> positiveTests() {
		return List.of(new HapiApiSpec[] {
				account2CanUpdateApplicationProperties(),
				account50CanUpdateApplicationProperties(),
				account2CanUpdateApiPermissions(),
				account50CanUpdateApiPermissions(),
				account2CanUpdateAddressBook(),
				account50CanUpdateAddressBook(),
				account55CanUpdateAddressBook(),
				account2CanUpdateNodeDetails(),
				account50CanUpdateNodeDetails(),
				account55CanUpdateNodeDetails(),
				account2CanUpdateFeeSchedule(),
				account50CanUpdateFeeSchedule(),
				account56CanUpdateFeeSchedule(),
				account2CanUpdateExchangeRates(),
				account50CanUpdateExchangeRates(),
				account57CanUpdateExchangeRates()
		});
	}

	private List<HapiApiSpec> negativeTests() {
		return Arrays.asList(
				unauthorizedAccountCannotUpdateApplicationProperties(),
				unauthorizedAccountCannotUpdateApiPermissions(),
				unauthorizedAccountCannotUpdateAddressBook(),
				unauthorizedAccountCannotUpdateNodeDetails(),
				unauthorizedAccountCannotUpdateFeeSchedule(),
				unauthorizedAccountCannotUpdateExchangeRates()
		);
	}

	private HapiApiSpec specialAccountCanUpdateSpecialPropertyFile(
			final String specialAccount,
			final String specialFile,
			final String property,
			final String expected
	) {
		return specialAccountCanUpdateSpecialPropertyFile(specialAccount, specialFile, property, expected, true);
	}

	private HapiApiSpec specialAccountCanUpdateSpecialPropertyFile(
			final String specialAccount,
			final String specialFile,
			final String property,
			final String expected,
			final boolean isFree
	) {
		return defaultHapiSpec(specialAccount + "CanUpdate" + specialFile)
				.given(
						givenOps(specialAccount, specialFile)
				)
				.when(
						fileUpdate(specialFile)
								.overridingProps(Map.of(property, expected))
								.payingWith(specialAccount)
				)
				.then(
						validateAndCleanUpOps(
								propertyFileValidationOp(specialAccount, specialFile, property, expected),
								specialAccount,
								specialFile,
								isFree)
				);
	}

	private HapiSpecOperation propertyFileValidationOp(
			String account,
			String fileName,
			String property,
			String expected
	) {
		return UtilVerbs.withOpContext((spec, ctxLog) -> {
			String registryEntry = fileName + "_CHANGED_BY_" + account;
			HapiGetFileContents subOp =
					getFileContents(fileName).saveToRegistry(registryEntry);
			CustomSpecAssert.allRunFor(spec, subOp);
			String newContents = new String(spec.registry().getBytes(registryEntry));
			int propertyIndex = newContents.indexOf(property);
			Assert.assertTrue(propertyIndex >= 0);
			int valueIndex = propertyIndex + property.length() + PROPERTY_VALUE_SPACE_LENGTH;
			String actual = newContents.substring(valueIndex, valueIndex + expected.length());
			Assert.assertEquals(expected, actual);
		});
	}
	private HapiApiSpec specialAccountCanUpdateSpecialFile(
			final String specialAccount,
			final String specialFile,
			final String target,
			final String replacement
	) {
		return specialAccountCanUpdateSpecialFile(specialAccount, specialFile, target, replacement, true);
	}

	private HapiApiSpec specialAccountCanUpdateSpecialFile(
			final String specialAccount,
			final String specialFile,
			final String target,
			final String replacement,
			final boolean isFree
	) {
		return specialAccountCanUpdateSpecialFile(
				specialAccount,
				specialFile,
				isFree,
				contents ->
					target.equals("ignore")
							? contents : (new String(contents).replace(target, replacement)).getBytes());
	}

	private HapiApiSpec specialAccountCanUpdateSpecialFile(
			final String specialAccount,
			final String specialFile,
			final boolean isFree,
			final UnaryOperator<byte[]> contentsTransformer
	) {
		final String newFileName = "NEW_" + specialFile;

		return defaultHapiSpec(specialAccount + "CanUpdate" + specialFile)
				.given(
						ArrayUtils.add(
								givenOps(specialAccount, specialFile),
								UtilVerbs.withOpContext((spec, ctxLog) -> {
									var origContents = spec.registry().getBytes(specialFile);
									var newContents = contentsTransformer.apply(origContents);
									spec.registry().saveBytes(newFileName, ByteString.copyFrom(newContents));
								})
						)
				)
				.when(
						UtilVerbs.updateLargeFile(specialAccount, specialFile, newFileName)
				)
				.then(
						validateAndCleanUpOps(
								getFileContents(specialFile).hasContents(newFileName),
								specialAccount,
								specialFile,
								isFree)
				);
	}


	private HapiSpecOperation[] givenOps(String account, String fileName) {
		HapiSpecOperation[] opsArray = {
				UtilVerbs.fundAnAccount(account),
				getFileContents(fileName).saveToRegistry(fileName),
				UtilVerbs.balanceSnapshot("preUpdate", account)
		};
		return opsArray;
	}

	private HapiSpecOperation[] validateAndCleanUpOps(
			final HapiSpecOperation validateOp,
			final String account,
			final String fileName,
			final boolean isFree) {
		HapiSpecOperation[] accountBalanceUnchanged = {
				getAccountBalance(account).hasTinyBars(changeFromSnapshot("preUpdate", 0))
		};
		HapiSpecOperation[] opsArray = {
				validateOp,
				UtilVerbs.updateLargeFile(account, fileName, fileName),
				getFileContents(fileName).hasContents(fileName)
		};
		if (account.equals(GENESIS) || !isFree) {
			return opsArray;
		}
		return ArrayUtils.addAll(accountBalanceUnchanged, opsArray);
	}

	private HapiApiSpec unauthorizedAccountCannotUpdateSpecialFile(
			final String specialFile,
			final String newContents
	) {
		return defaultHapiSpec("UnauthorizedAccountCannotUpdate" + specialFile)
				.given(
						cryptoCreate("unauthorizedAccount")
				)
				.when()
				.then(
						fileUpdate(specialFile)
								.contents(newContents)
								.payingWith("unauthorizedAccount")
								.hasPrecheck(AUTHORIZATION_FAILED)
				);
	}

	private HapiApiSpec account2CanUpdateApplicationProperties() {
		return specialAccountCanUpdateSpecialPropertyFile(GENESIS, APP_PROPERTIES, "throttlingTps", "10");
	}

	private HapiApiSpec account50CanUpdateApplicationProperties() {
		return specialAccountCanUpdateSpecialPropertyFile(SYSTEM_ADMIN, APP_PROPERTIES, "getReceiptTps", "100");
	}

	private HapiApiSpec unauthorizedAccountCannotUpdateApplicationProperties() {
		return unauthorizedAccountCannotUpdateSpecialFile(APP_PROPERTIES, "newContents");
	}

	private HapiApiSpec account2CanUpdateApiPermissions() {
		return specialAccountCanUpdateSpecialPropertyFile(GENESIS, API_PERMISSIONS, "createTopic", "1-*");
	}

	private HapiApiSpec account50CanUpdateApiPermissions() {
		return specialAccountCanUpdateSpecialPropertyFile(SYSTEM_ADMIN, API_PERMISSIONS, "updateFile", "1-*");
	}

	private HapiApiSpec unauthorizedAccountCannotUpdateApiPermissions() {
		return unauthorizedAccountCannotUpdateSpecialFile(API_PERMISSIONS, "newContents");
	}

	private HapiApiSpec account2CanUpdateAddressBook() {
		return specialAccountCanUpdateSpecialFile(
				GENESIS,
				ADDRESS_BOOK,
				true,
				contents -> extendedBioAddressBook(contents, "0.0.5", "0.0.6"));
	}

	private HapiApiSpec account50CanUpdateAddressBook() {
		return specialAccountCanUpdateSpecialFile(SYSTEM_ADMIN, ADDRESS_BOOK, "0.0.5", "0.0.6");
	}

	private HapiApiSpec account55CanUpdateAddressBook() {
		return specialAccountCanUpdateSpecialFile(ADDRESS_BOOK_CONTROL, ADDRESS_BOOK, "0.0.5", "0.0.6", false);
	}

	private HapiApiSpec unauthorizedAccountCannotUpdateAddressBook() {
		return unauthorizedAccountCannotUpdateSpecialFile(ADDRESS_BOOK, "newContents");
	}

	private HapiApiSpec account2CanUpdateNodeDetails() {
		return specialAccountCanUpdateSpecialFile(
				GENESIS,
				NODE_DETAILS,
				true,
				contents -> extendedBioNodeDetails(contents, "0.0.5", "0.0.6"));
	}

	private HapiApiSpec account50CanUpdateNodeDetails() {
		return specialAccountCanUpdateSpecialFile(SYSTEM_ADMIN, NODE_DETAILS, "0.0.5", "0.0.6");
	}

	private HapiApiSpec account55CanUpdateNodeDetails() {
		return specialAccountCanUpdateSpecialFile(ADDRESS_BOOK_CONTROL, NODE_DETAILS, "0.0.5", "0.0.6", false);
	}

	private HapiApiSpec unauthorizedAccountCannotUpdateNodeDetails() {
		return unauthorizedAccountCannotUpdateSpecialFile(NODE_DETAILS, "newContents");
	}

	private HapiApiSpec account2CanUpdateFeeSchedule() {
		return specialAccountCanUpdateSpecialFile(GENESIS, FEE_SCHEDULE, "ignore", "ignore");
	}

	private HapiApiSpec account50CanUpdateFeeSchedule() {
		return specialAccountCanUpdateSpecialFile(SYSTEM_ADMIN, FEE_SCHEDULE, "ignore", "ignore");
	}

	private HapiApiSpec account56CanUpdateFeeSchedule() {
		return specialAccountCanUpdateSpecialFile(FEE_SCHEDULE_CONTROL, FEE_SCHEDULE, "ignore", "ignore");
	}

	private HapiApiSpec unauthorizedAccountCannotUpdateFeeSchedule() {
		return unauthorizedAccountCannotUpdateSpecialFile(FEE_SCHEDULE, "newContents");
	}

	private HapiApiSpec account2CanUpdateExchangeRates() {
		return specialAccountCanUpdateSpecialFile(GENESIS, EXCHANGE_RATES, "ignore", "ignore");
	}

	private HapiApiSpec account50CanUpdateExchangeRates() {
		return specialAccountCanUpdateSpecialFile(SYSTEM_ADMIN, EXCHANGE_RATES, "ignore", "ignore");
	}

	private HapiApiSpec account57CanUpdateExchangeRates() {
		return specialAccountCanUpdateSpecialFile(EXCHANGE_RATE_CONTROL, EXCHANGE_RATES, "ignore", "ignore");
	}

	private HapiApiSpec unauthorizedAccountCannotUpdateExchangeRates() {
		return unauthorizedAccountCannotUpdateSpecialFile(EXCHANGE_RATES, "newContents");
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	private byte[] extendedBioAddressBook(byte[] contents, String targetMemo, String replaceMemo) {
		var r = new SplittableRandom();
		try {
			var book = AddressBook.parseFrom(contents);
			var builder = book.toBuilder();
			byte[] randCertHash = new byte[32];
			long nodeId = 0;
			for (NodeAddress.Builder node : builder.getNodeAddressBuilderList()) {
				node.setNodeId(nodeId++);
				r.nextBytes(randCertHash);
				node.setNodeCertHash(ByteString.copyFrom(randCertHash));
				node.setNodeAccountId(HapiPropertySource.asAccount(new String(node.getMemo().toByteArray())));
				if (new String(node.getMemo().toByteArray()).equals(targetMemo)) {
					node.setMemo(ByteString.copyFrom(replaceMemo.getBytes()));
				}
			}
			var newBook = builder.build();
			var bookJson = mapper
					.writerWithDefaultPrettyPrinter()
					.writeValueAsString(AddressBookPojo.addressBookFrom(newBook));
			log.info("New address book w/ extended bio: " + bookJson);
			return builder.build().toByteArray();
		} catch (InvalidProtocolBufferException e) {
			log.error("Basic address book could not be parsed", e);
			throw new AssertionError("Unparseable address book!");
		} catch (JsonProcessingException e) {
			log.error("Extended address book could not be serialized", e);
			throw new AssertionError("Unserializable address book!");
		}
	}

	private byte[] extendedBioNodeDetails(byte[] contents, String targetMemo, String replaceMemo) {
		var r = new SplittableRandom();
		try {
			var book = AddressBook.parseFrom(contents);
			var builder = book.toBuilder();
			byte[] randPubKey = new byte[422];
			long nodeId = 0;
			for (NodeAddress.Builder node : builder.getNodeAddressBuilderList()) {
				node.setNodeId(nodeId++);
				r.nextBytes(randPubKey);
				node.setRSAPubKey(Hex.encodeHexString(randPubKey));
				if (new String(node.getMemo().toByteArray()).equals(targetMemo)) {
					node.setMemo(ByteString.copyFrom(replaceMemo.getBytes()));
				}
			}
			var newBook = builder.build();
			var bookJson = mapper
					.writerWithDefaultPrettyPrinter()
					.writeValueAsString(AddressBookPojo.nodeDetailsFrom(newBook));
			log.info("New node details w/ extended bio: " + bookJson);
			return builder.build().toByteArray();
		} catch (InvalidProtocolBufferException e) {
			log.error("Basic node details could not be parsed", e);
			throw new AssertionError("Unparseable node details!");
		} catch (JsonProcessingException e) {
			log.error("Extended node details could not be serialized", e);
			throw new AssertionError("Unserializable node details!");
		}
	}
}
