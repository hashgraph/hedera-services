package virtual;

import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractKeySerializer;
import com.hedera.services.state.virtual.ContractKeySupplier;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.ContractValueSupplier;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.jasperdb.DataSourceValidator;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.jasperdb.VirtualDataSourceJasperDB;
import com.swirlds.jasperdb.VirtualInternalRecordSerializer;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.DataFileCommon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * App for validating all Contract data in a database in current working directory.
 */
public class ContractDataSourceValidator {
	public static void main(String[] args) throws IOException {
		Path dataSourcePath = Path.of("").toAbsolutePath();
		System.out.println("dataSourcePath = " + dataSourcePath + " exists " + Files.exists(dataSourcePath));


		VirtualLeafRecordSerializer<ContractKey, ContractValue> virtualLeafRecordSerializer =
				new VirtualLeafRecordSerializer<>(
						(short) 1, DigestType.SHA_384,
						(short) 1, DataFileCommon.VARIABLE_DATA_SIZE, new ContractKeySupplier(),
						(short) 1, ContractValue.SERIALIZED_SIZE, new ContractValueSupplier(),
						true);
		;

		JasperDbBuilder<ContractKey, ContractValue> dbBuilder = new JasperDbBuilder<>();
		dbBuilder
				.virtualLeafRecordSerializer(virtualLeafRecordSerializer)
				.virtualInternalRecordSerializer(new VirtualInternalRecordSerializer())
				.keySerializer(new ContractKeySerializer())
				.storageDir(dataSourcePath)
				.maxNumOfKeys(500_000_000)
				.preferDiskBasedIndexes(false)
				.internalHashesRamToDiskThreshold(0)
				.mergingEnabled(true);

		DataSourceValidator<ContractKey, ContractValue> dataSourceValidator =
				new DataSourceValidator<>(dbBuilder.build("jdb", "4validator"));
		dataSourceValidator.validate();
	}
}
