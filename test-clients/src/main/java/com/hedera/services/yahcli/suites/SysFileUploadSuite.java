package com.hedera.services.yahcli.suites;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.sysfiles.AddressBookPojo;
import com.hedera.services.bdd.suites.utils.sysfiles.BookEntryPojo;
import com.hedera.services.bdd.suites.utils.sysfiles.ExchangeRatesPojo;
import com.hedera.services.bdd.suites.utils.sysfiles.FeeScheduleDeJson;
import com.hedera.services.bdd.suites.utils.sysfiles.serdes.JutilPropsToSvcCfgBytes;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileAppend;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class SysFileUploadSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(SysFileUploadSuite.class);

	private static final String ADDRESS_BOOK_FILE_NAME = "addressBook.json";
	private static final String NODE_DETAILS_FILE_NAME = "nodeDetails.json";
	private static final String EXCHANGE_RATES_FILE_NAME = "exchangeRates.json";
	private static final String FEE_SCEDULES_FILE_NAME = "feeSchedules.json";
	private static final String APP_PROPERTIES_FILE_NAME = "application.properties";
	private static final String API_PERMISSION_FILE_NAME = "api-permission.properties";

	static Map<String, String> registryNames = new HashMap<>(Map.of(
			ADDRESS_BOOK_FILE_NAME, ADDRESS_BOOK,
			NODE_DETAILS_FILE_NAME, NODE_DETAILS,
			EXCHANGE_RATES_FILE_NAME, EXCHANGE_RATES,
			FEE_SCEDULES_FILE_NAME, FEE_SCHEDULE,
			APP_PROPERTIES_FILE_NAME, APP_PROPERTIES,
			API_PERMISSION_FILE_NAME,  API_PERMISSIONS));

	static Map<String, Function<BookEntryPojo, Stream<NodeAddress>>> updateConverters = new HashMap<>(Map.of(
			"addressBook.json", BookEntryPojo::toAddressBookEntries,
			"nodeDetails.json", BookEntryPojo::toNodeDetailsEntry));

	static ObjectMapper mapper = new ObjectMapper();

	private final String srcDir;
	private final String sysFile;
	private final Map<String, String> specConfig;

	public SysFileUploadSuite(final String srcDir, final Map<String, String> specConfig, final String sysFile) {
		this.srcDir = srcDir;
		this.specConfig = specConfig;
		this.sysFile = sysFile;
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				uploadSysFiles(),
		});
	}

	private HapiApiSpec uploadSysFiles() {
		final byte[] toUpload = getUploadBytes();

		return HapiApiSpec.customHapiSpec(String.format("UploadSystemFile-%s", sysFile)).withProperties(
				specConfig
		).given().when().then(
				withOpContext((spec, opLog) -> {
					if (toUpload.length < (6 * 1024)) {
						var singleOp = fileUpdate(registryNames.get(sysFile))
								.payingWith(DEFAULT_PAYER)
								.fee(10_000_000_000L)
								.contents(toUpload)
								.signedBy(DEFAULT_PAYER);
						CustomSpecAssert.allRunFor(spec, singleOp);
					} else {
						int n = 0;
						while (n < toUpload.length) {
							int thisChunkSize = Math.min(toUpload.length - n, 4096);
							byte[] thisChunk = Arrays.copyOfRange(toUpload, n, n + thisChunkSize);
							HapiSpecOperation subOp;
							if (n == 0) {
								subOp = fileUpdate(registryNames.get(sysFile))
										.fee(10_000_000_000L)
										.wacl("insurance")
										.contents(thisChunk)
										.signedBy(DEFAULT_PAYER)
										.hasKnownStatusFrom(SUCCESS, FEE_SCHEDULE_FILE_PART_UPLOADED);
							} else {
								subOp = fileAppend(registryNames.get(sysFile))
										.fee(10_000_000_000L)
										.content(thisChunk)
										.signedBy(DEFAULT_PAYER)
										.hasKnownStatusFrom(SUCCESS, FEE_SCHEDULE_FILE_PART_UPLOADED);
							}
							CustomSpecAssert.allRunFor(spec, subOp);
							n += thisChunkSize;
						}
					}
				})
		);
	}

	private byte[] getUploadBytes() {
		byte[] bytes = new byte[0];
		String fileToUploadPath = srcDir + File.separator + sysFile;
		try{
			switch(sysFile) {
				case ADDRESS_BOOK_FILE_NAME:
				case NODE_DETAILS_FILE_NAME:
					AddressBookPojo pojoBook = mapper.readValue(new File(fileToUploadPath), AddressBookPojo.class);
					NodeAddressBook.Builder addressBook = NodeAddressBook.newBuilder();
					pojoBook.getEntries().stream()
							.flatMap(updateConverters.get(sysFile))
							.forEach(addressBook::addNodeAddress);
					bytes = addressBook.build().toByteArray();
					break;
				case EXCHANGE_RATES_FILE_NAME:
					var pojoRates = mapper.readValue(new File(fileToUploadPath), ExchangeRatesPojo.class);
					bytes = pojoRates.toProto().toByteArray();
					break;
				case FEE_SCEDULES_FILE_NAME:
					bytes = FeeScheduleDeJson.fromJson(fileToUploadPath).toByteArray();
					break;
				case APP_PROPERTIES_FILE_NAME:
				case API_PERMISSION_FILE_NAME:
					var jutilConfig = new Properties();
					jutilConfig.load(java.nio.file.Files.newInputStream(Paths.get(fileToUploadPath)));
					ServicesConfigurationList.Builder protoConfig = ServicesConfigurationList.newBuilder();
					jutilConfig.stringPropertyNames()
							.stream()
							.sorted(JutilPropsToSvcCfgBytes.LEGACY_THROTTLES_FIRST_ORDER)
							.forEach(prop -> protoConfig.addNameValue(Setting.newBuilder()
									.setName(prop)
									.setValue(jutilConfig.getProperty(prop))));
					bytes = protoConfig.build().toByteArray();
					break;
			}
		} catch (Exception e) {
			System.out.println(
					String.format("File '%s' should contain a human-readable representation!",
							fileToUploadPath));
			e.printStackTrace();
			System.exit(1);
		}
		return bytes;
	}
}
