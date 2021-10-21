package virtual;

import com.hedera.services.state.merkle.virtual.ContractKey;
import com.hedera.services.state.merkle.virtual.ContractKeySerializer;
import com.hedera.services.state.merkle.virtual.ContractValue;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.jasperdb.DataSourceValidator;
import com.swirlds.jasperdb.VirtualDataSourceJasperDB;
import com.swirlds.jasperdb.VirtualInternalRecordSerializer;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.DataFileCommon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * App for validating all Contract data in a database in current working directory.
 */
public class ContractDataSourceValidator {
    public static void main(String[] args) throws IOException {
        Path dataSourcePath = Path.of("").toAbsolutePath();
        System.out.println("dataSourcePath = " + dataSourcePath+" exists "+ Files.exists(dataSourcePath));


        VirtualLeafRecordSerializer<ContractKey, ContractValue> virtualLeafRecordSerializer =
                new VirtualLeafRecordSerializer<>(
                        (short) 1, DigestType.SHA_384,
                        (short) 1, DataFileCommon.VARIABLE_DATA_SIZE,ContractKey::new,
                        (short) 1,ContractValue.SERIALIZED_SIZE,ContractValue::new,
                        true);;
        VirtualDataSourceJasperDB<ContractKey, ContractValue> dataSourceJasperDB = new VirtualDataSourceJasperDB<>(
                virtualLeafRecordSerializer,
                new VirtualInternalRecordSerializer(),
                new ContractKeySerializer(),
                dataSourcePath,
                500_000_000,
                true,
                0,
                false);

        DataSourceValidator<ContractKey, ContractValue> dataSourceValidator = new DataSourceValidator<>(dataSourceJasperDB);
        dataSourceValidator.validate();
    }
}
