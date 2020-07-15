package com.hedera.services.bdd.suites.utils.sysfiles;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Files;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;

import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.keypairs.Ed25519KeyStore;
import com.hedera.services.bdd.suites.utils.keypairs.SpecUtils;
import com.hedera.services.bdd.suites.utils.sysfiles.serdes.JutilPropsToSvcCfgBytes;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.PropertySource.asAccount;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileAppend;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class SysFilesUpdate extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(SysFilesUpdate.class);

	final static long TINYBARS_PER_HBAR = 100_000_000L;
	final static long DEFAULT_FEE_IN_HBARS = 100L;

	final static String DEV_TARGET_DIR = "/Users/tinkerm/Dev/misc/tools/scratch";

	static Action action;
	static SystemFile target;
	static String feeScheduleLoc;
	static String defaultPayerOverride;
	static String startupAccountsPathOverride;
	static String DEFAULT_SYSFILE_KEY = "defaultSysFile";
	static String DEFAULT_SYSFILE_PEM_LOC = "defaultSysFile.pem";

	static ObjectMapper mapper = new ObjectMapper();

	enum Action {
		DOWNLOAD, UPDATE, UNKNOWN
	}

	enum SystemFile {
		ADDRESS_BOOK, NODE_DETAILS, APPLICATION_PROPERTIES, API_PERMISSIONS, EXCHANGE_RATES, FEE_SCHEDULE
	}

	static EnumSet<SystemFile> BOOK_FILES = EnumSet.of(SystemFile.ADDRESS_BOOK, SystemFile.NODE_DETAILS);

	static EnumMap<SystemFile, String> humanReadableFileNames = new EnumMap<>(Map.of(
			SystemFile.ADDRESS_BOOK, "addressBook.json",
			SystemFile.NODE_DETAILS, "nodeDetails.json",
			SystemFile.EXCHANGE_RATES, "exchangeRates.json",
			SystemFile.FEE_SCHEDULE, "feeSchedule.txt",
			SystemFile.APPLICATION_PROPERTIES, "application.properties",
			SystemFile.API_PERMISSIONS, "api-permission.properties"));
	static EnumMap<SystemFile, String> protoFileNames = new EnumMap<>(Map.of(
			SystemFile.ADDRESS_BOOK, "addressBook.bin",
			SystemFile.NODE_DETAILS, "nodeDetails.bin",
			SystemFile.EXCHANGE_RATES, "exchangeRates.bin",
			SystemFile.FEE_SCHEDULE, "feeSchedule.bin",
			SystemFile.APPLICATION_PROPERTIES, "applicationProperties.bin",
			SystemFile.API_PERMISSIONS, "apiPermissions.bin"));
	static EnumMap<SystemFile, String> registryNames = new EnumMap<>(Map.of(
			SystemFile.ADDRESS_BOOK, ADDRESS_BOOK,
			SystemFile.NODE_DETAILS, NODE_DETAILS,
			SystemFile.EXCHANGE_RATES, EXCHANGE_RATES,
			SystemFile.FEE_SCHEDULE, FEE_SCHEDULE,
			SystemFile.APPLICATION_PROPERTIES, APP_PROPERTIES,
			SystemFile.API_PERMISSIONS, API_PERMISSIONS));

	static EnumMap<SystemFile, Function<BookEntryPojo, Stream<NodeAddress>>> updateConverters = new EnumMap<>(Map.of(
			SystemFile.ADDRESS_BOOK, BookEntryPojo::toAddressBookEntries,
			SystemFile.NODE_DETAILS, BookEntryPojo::toNodeDetailsEntry));

	static Pattern nodeCertPattern = Pattern.compile(".*node(\\d+)[.]crt");
	static Pattern pubKeyPattern = Pattern.compile(".*node(\\d+).]der");

	public static void main(String... args) throws IOException {
		parse(args);
		if (action == Action.UNKNOWN) {
			System.exit(1);
		}
		writeDefaultSysFilesPem();
		new SysFilesUpdate().runSuiteSync();

		if (action == Action.DOWNLOAD) {
			if (target == SystemFile.ADDRESS_BOOK) {
				dumpAvailCerts();
			} else if (target == SystemFile.NODE_DETAILS) {
				dumpAvailPubKeys();
			}
		}
	}

	public static long feeToOffer() {
		return Optional.ofNullable(System.getenv("TXN_FEE"))
				.map(s -> Long.parseLong(s) * TINYBARS_PER_HBAR)
				.orElse(DEFAULT_FEE_IN_HBARS * TINYBARS_PER_HBAR);
	}

	private static void writeDefaultSysFilesPem() throws IOException {
		var pemOut = java.nio.file.Files.newOutputStream(
				java.nio.file.Paths.get(DEFAULT_SYSFILE_PEM_LOC));
		var pemIn = SysFilesUpdate.class.getClassLoader().getResourceAsStream("genesis.pem");
		pemIn.transferTo(pemOut);
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		switch (action) {
			case DOWNLOAD:
				return List.of(downloadTargetAsHumanFriendly());
			case UPDATE:
				return List.of(updateTargetFromHumanReadable());
			default:
				throw new AssertionError("Impossible action!");
		}
	}

	private String qualifiedPath(String nodesOverride, String basename, boolean forDownload) {
		return (!forDownload && target == SystemFile.FEE_SCHEDULE)
				? feeScheduleLoc
				: path(String.format("%s-%s", nodesOverride.replace(":", "_"), basename));
	}

	private HapiApiSpec downloadTargetAsHumanFriendly() {
		String nodesOverride = envNodes();
		String binFile = qualifiedPath(nodesOverride, protoFileNames.get(target), true);
		String humanReadableFile = qualifiedPath(nodesOverride, humanReadableFileNames.get(target), true);

		return customHapiSpec(String.format("Download-%s-ToHumanReadable", target.toString()))
				.withProperties(Map.of(
						"nodes", nodesOverride,
						"default.payer", defaultPayerOverride,
						"startupAccounts.path", startupAccountsPathOverride
				)).given().when().then(
						getFileInfo(registryNames.get(target)).logged(),
						getFileContents(registryNames.get(target))
								.saveTo(binFile)
								.saveReadableTo(unchecked(SysFilesUpdate::asHumanReadable), humanReadableFile)
				);
	}

	private HapiApiSpec updateTargetFromHumanReadable() {
		byte[] bytes = new byte[0];

		String nodesOverride = envNodes();
		System.out.println("Targeting " + nodesOverride);
		String loc = humanReadableFileNames.get(target);
		String readableFile = qualifiedPath(nodesOverride, loc, false);
		try {
			if (BOOK_FILES.contains(target)) {
				AddressBookPojo pojoBook = mapper.readValue(new File(readableFile), AddressBookPojo.class);
				NodeAddressBook.Builder addressBook = NodeAddressBook.newBuilder();
				pojoBook.getEntries().stream()
						.flatMap(updateConverters.get(target))
						.forEach(addressBook::addNodeAddress);
				bytes = addressBook.build().toByteArray();
			} else if (target == SystemFile.EXCHANGE_RATES) {
				var pojoRates = mapper.readValue(new File(readableFile), ExchangeRatesPojo.class);
				bytes = pojoRates.toProto().toByteArray();
			} else if (target == SystemFile.FEE_SCHEDULE) {
				bytes = FeeScheduleDeJson.fromJson(readableFile).toByteArray();
			} else {
				var jutilConfig = new Properties();
				jutilConfig.load(java.nio.file.Files.newInputStream(Paths.get(readableFile)));
				ServicesConfigurationList.Builder protoConfig = ServicesConfigurationList.newBuilder();
				jutilConfig.stringPropertyNames()
						.stream()
						.sorted(JutilPropsToSvcCfgBytes.LEGACY_THROTTLES_FIRST_ORDER)
						.forEach(prop -> protoConfig.addNameValue(Setting.newBuilder()
								.setName(prop)
								.setValue(jutilConfig.getProperty(prop))));
				bytes = protoConfig.build().toByteArray();
			}
		} catch (Exception e) {
			System.out.println(
					String.format(
							"File '%s' should contain a human-readable %s representation!",
							readableFile,
							target.toString()));
			e.printStackTrace();
			System.exit(1);
		}

		final byte[] toUpload = bytes;

		return customHapiSpec(String.format("Update-%s-FromHumanReadable", target))
				.withProperties(Map.of(
						"nodes", nodesOverride,
						"default.payer", defaultPayerOverride,
						"startupAccounts.path", startupAccountsPathOverride
				)).given(
						withOpContext((spec, opLog) -> {
							var sysFileOcKeystore = SpecUtils.asOcKeystore(
									new File(DEFAULT_SYSFILE_PEM_LOC),
									KeyFactory.PEM_PASSPHRASE);
							var sysFileKey = Key.newBuilder()
									.setEd25519(
											ByteString.copyFrom(Hex.decodeHex(sysFileOcKeystore.getPublicKeyAbyteStr())))
									.build();
							opLog.info("Will ensure default public key :: " + sysFileKey
									+ " (hex = " + sysFileOcKeystore.getPublicKeyAbyteStr() + ")");
							spec.registry().saveKey(DEFAULT_SYSFILE_KEY, sysFileKey);
							spec.keys().incorporate(DEFAULT_SYSFILE_KEY, sysFileOcKeystore);
						}),
						keyFromPem(DEFAULT_SYSFILE_PEM_LOC)
								.name("insurance")
								.simpleWacl()
								.passphrase(KeyFactory.PEM_PASSPHRASE)
				).when().then(
						withOpContext((spec, opLog) -> {
							if (toUpload.length < (6 * 1024)) {
								var singleOp = fileUpdate(registryNames.get(target))
										.fee(feeToOffer())
										.contents(toUpload)
										.signedBy(GENESIS, DEFAULT_SYSFILE_KEY);
								CustomSpecAssert.allRunFor(spec, singleOp);
							} else {
								int n = 0;
								while (n < toUpload.length) {
									int thisChunkSize = Math.min(toUpload.length - n, 4096);
									byte[] thisChunk = Arrays.copyOfRange(toUpload, n, n + thisChunkSize);
									HapiSpecOperation subOp;
									if (n == 0) {
										subOp = fileUpdate(registryNames.get(target))
												.fee(feeToOffer())
												.wacl("insurance")
												.contents(thisChunk)
												.signedBy(GENESIS, DEFAULT_SYSFILE_KEY)
												.hasKnownStatusFrom(SUCCESS, FEE_SCHEDULE_FILE_PART_UPLOADED);
									} else {
										subOp = fileAppend(registryNames.get(target))
												.fee(feeToOffer())
												.content(thisChunk)
												.signedBy(GENESIS, DEFAULT_SYSFILE_KEY)
												.hasKnownStatusFrom(SUCCESS, FEE_SCHEDULE_FILE_PART_UPLOADED);
									}
									CustomSpecAssert.allRunFor(spec, subOp);
									n += thisChunkSize;
								}
							}
						})
				);
	}

	private static Object asHumanReadable(byte[] bytes) throws Exception {
		if (BOOK_FILES.contains(target)) {
			var proto = NodeAddressBook.parseFrom(bytes);
			var pojoBook = (target == SystemFile.ADDRESS_BOOK)
					? AddressBookPojo.addressBookFrom(proto)
					: AddressBookPojo.nodeDetailsFrom(proto);
			return mapper
					.writerWithDefaultPrettyPrinter()
					.writeValueAsString(pojoBook);
		} else if (target == SystemFile.EXCHANGE_RATES) {
			var pojoRates = ExchangeRatesPojo.fromProto(ExchangeRateSet.parseFrom(bytes));
			return mapper
					.writerWithDefaultPrettyPrinter()
					.writeValueAsString(pojoRates);
		} else if (target == SystemFile.FEE_SCHEDULE) {
			return CurrentAndNextFeeSchedule.parseFrom(bytes).toString();
		} else {
			var proto = ServicesConfigurationList.parseFrom(bytes);
			return proto.getNameValueList()
					.stream()
					.map(setting -> String.format("%s=%s", setting.getName(), setting.getValue()))
					.sorted(JutilPropsToSvcCfgBytes.LEGACY_THROTTLES_FIRST_ORDER)
					.collect(Collectors.joining("\n"));
		}
	}

	private String envNodes() {
		String host = Optional.ofNullable(System.getenv("TARGET_NODE"))
				.orElse("127.0.0.1");
		String port = Optional.ofNullable(System.getenv("NODE_PORT")).orElse("50211");
		return String.format("%s:%s", host, port);
	}

	@FunctionalInterface
	private interface CheckedParser {
		Object parseFrom(byte[] bytes) throws Exception;
	}

	private Function<byte[], String> unchecked(CheckedParser parser) {
		return bytes -> {
			try {
				return parser.parseFrom(bytes).toString();
			} catch (Exception e) {
				e.printStackTrace();
				return "<N/A> due to " + e.getMessage() + "!";
			}
		};
	}

	private String path(String file) {
		return Path.of(targetDir(), file).toString();
	}

	private String targetDir() {
		return Optional.ofNullable(System.getenv("SCRATCH_PATH")).orElse(DEV_TARGET_DIR);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	private static void parse(String[] args) {
		action = Action.UNKNOWN;
		try {
			action = Action.valueOf(args[0]);
			defaultPayerOverride = args[1];
			if (!TxnUtils.isIdLiteral(defaultPayerOverride)) {
				System.out.println(
						String.format(
								"Args (%s) don't have a valid payer override at position 2!",
								Arrays.toString(args)));
				System.exit(1);
			}
			var keyPairFile = new File(args[2]);
			if (!keyPairFile.exists()) {
				System.out.println(
						String.format(
								"Args (%s) don't include an extant keypair file at position 3!",
								Arrays.toString(args)));
				System.exit(1);
			}
			startupAccountsPathOverride = args[2];
			if (startupAccountsPathOverride.endsWith(".pem")) {
				b64EncodePem();
			}
			target = SystemFile.ADDRESS_BOOK;
			if (args.length >= 4) {
				if ("102".equals(args[3])) {
					target = SystemFile.NODE_DETAILS;
				} else if ("121".equals(args[3])) {
					target = SystemFile.APPLICATION_PROPERTIES;
				} else if ("122".equals(args[3])) {
					target = SystemFile.API_PERMISSIONS;
				} else if ("112".equals(args[3])) {
					target = SystemFile.EXCHANGE_RATES;
				} else if ("111".equals(args[3])) {
					target = SystemFile.FEE_SCHEDULE;
					if (action == Action.UPDATE) {
						if (args.length != 5) {
							System.out.println(
									String.format(
											"Args (%s) don't include a path to the fee schedule JSON for the update!",
											Arrays.toString(args)));
							System.exit(1);
						}
						feeScheduleLoc = args[4];
					}
				}
			}
		} catch (Exception e) {
			System.out.println(
					String.format(
							"Args (%s) should begin with the 'UPDATE' or 'DOWNLOAD' action!",
							Arrays.toString(args)));
		}
	}

	private static void b64EncodePem() {
		var pemFile = new File(startupAccountsPathOverride);
		var baseName = startupAccountsPathOverride.substring(0, startupAccountsPathOverride.length() - 4);
		if (baseName.lastIndexOf('/') != -1) {
			baseName = baseName.substring(baseName.lastIndexOf('/') + 1);
		}
		baseName = baseName.substring(0, 1).toUpperCase() + baseName.substring(1);
		startupAccountsPathOverride = String.format("b64%sKeyPair.txt", baseName);
		var passphrase = Optional.ofNullable(System.getenv("PEM_PASSPHRASE")).orElse("swirlds");
		try {
			var keyPairFile = new File(startupAccountsPathOverride);
			var keyStore = Ed25519KeyStore.read(passphrase.toCharArray(), pemFile);
			var keyPair = keyStore.get(0);

			writeB64EncodedKeyPair(keyPairFile, keyPair);
		} catch (Exception e) {
			System.out.println("Could not encode '" + pemFile.toString() + "' as a usable keypair!");
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static void writeB64EncodedKeyPair(File file, KeyPair keyPair) throws Exception {
		var hexPublicKey = Hex.encodeHexString(keyPair.getPublic().getEncoded());
		var hexPrivateKey = Hex.encodeHexString(keyPair.getPrivate().getEncoded());
		var keyPairObj = new KeyPairObj(hexPublicKey, hexPrivateKey);
		var keys = new AccountKeyListObj(asAccount(defaultPayerOverride), List.of(keyPairObj));

		var baos = new ByteArrayOutputStream();
		var oos = new ObjectOutputStream(baos);
		oos.writeObject(Map.of("START_ACCOUNT", List.of(keys)));
		oos.close();

		var byteSink = Files.asByteSink(file);
		byteSink.write(CommonUtils.base64encode(baos.toByteArray()).getBytes());
	}

	private static void dumpAvailPubKeys() throws IOException {
		var pubKeysDirDir = new File(BookEntryPojo.RSA_PUBKEYS_DIR);
		if (!pubKeysDirDir.exists()) {
			System.out.println(
					String.format("Missing dir '%s/', rsaPubKey fields cannot be auto-generated with '!'"));
			return;
		}
		System.out.println("When updating this address book, use '!' in the JSON rsaPubKey field to insert "
				+ "the corresponding (hex-encoded) RSA pub key in DER syntax: ");
		for (Path pubKeyLoc : allPubKeyPaths()) {
			var matcher = pubKeyPattern.matcher(pubKeyLoc.toString());
			matcher.matches();
			long nodeId = Long.valueOf(matcher.group(1));
			System.out.println(String.format("From '%s', %s", pubKeyLoc, BookEntryPojo.asHexEncodedDerPubKey(nodeId)));
		}

	}

	private static void dumpAvailCerts() throws IOException {
		var certsDir = new File(BookEntryPojo.CRTS_DIR);
		if (!certsDir.exists()) {
			System.out.println(
					String.format("Missing dir '%s/', certHash fields cannot be auto-generated with '!'"));
			return;
		}
		System.out.println("When updating this address book, use '!' in the JSON certHash field to insert "
				+ "the corresponding (hex-encoded) SHA-384 hash: ");
		for (Path crtLoc : allCertFiles()) {
			var matcher = nodeCertPattern.matcher(crtLoc.toString());
			matcher.matches();
			long nodeId = Long.valueOf(matcher.group(1));
			System.out.println(String.format("From '%s', %s", crtLoc, BookEntryPojo.asHexEncodedSha384HashFor(nodeId)));
		}

	}

	private static List<Path> allPubKeyPaths() throws IOException {
		return java.nio.file.Files.walk(Paths.get(BookEntryPojo.RSA_PUBKEYS_DIR))
				.filter(path -> path.toString().endsWith(".der"))
				.collect(Collectors.toList());
	}

	private static List<Path> allCertFiles() throws IOException {
		return java.nio.file.Files.walk(Paths.get(BookEntryPojo.CRTS_DIR))
				.filter(path -> path.toString().endsWith(".crt"))
				.collect(Collectors.toList());
	}

}
