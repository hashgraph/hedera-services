package com.hedera.services.state.exports;

import com.hedera.services.ServicesState;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;

import static com.hedera.services.utils.EntityIdUtils.readableId;

public class SignedStateBalancesExporter implements BalancesExporter {
	static Logger log = LogManager.getLogger(SignedStateBalancesExporter.class);

	static final String UNKNOWN_EXPORT_DIR = "";
	static final Instant NEVER = null;

	final long expectedFloat;
	final GlobalDynamicProperties dynamicProperties;

	String lastUsedExportDir = "";
	Instant periodEnd = NEVER;
	DirectoryAssurance directories = loc -> Files.createDirectories(Paths.get(loc));

	public SignedStateBalancesExporter(
			PropertySource properties,
			GlobalDynamicProperties dynamicProperties
	) {
		this.expectedFloat = properties.getLongProperty("ledger.totalTinyBarFloat");
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public boolean isTimeToExport(Instant now) {
		if (!dynamicProperties.shouldExportBalances()) {
			return false;
		}
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
	public void toCsvFile(ServicesState signedState, Instant when) {
		ensureExportDir(signedState.getNodeAccountId());
	}

	private void ensureExportDir(AccountID node) {
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
			} catch (IOException e) {
				throw new AssertionError("Not implemented");
			}
		}
	}
}
