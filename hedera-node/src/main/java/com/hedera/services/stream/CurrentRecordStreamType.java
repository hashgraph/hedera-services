package com.hedera.services.stream;

import com.hedera.services.context.properties.SemanticVersions;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;

@Singleton
public class CurrentRecordStreamType implements RecordStreamType {
	private static final Logger log = LogManager.getLogger(CurrentRecordStreamType.class);

	private final SemanticVersions semanticVersions;

	private int[] fileHeader = null;

	@Inject
	public CurrentRecordStreamType(final SemanticVersions semanticVersions) {
		this.semanticVersions = semanticVersions;
	}

	@Override
	public int[] getFileHeader() {
		if (fileHeader == null) {
			final var deployed = semanticVersions.getDeployed();
			final var protoSemVer = deployed.protoSemVer();
			if (SemanticVersion.getDefaultInstance().equals(protoSemVer)) {
				log.error("Failed to load HAPI proto versions, record stream files may be unusable");
			}
			fileHeader = new int[] {
					RECORD_VERSION,
					protoSemVer.getMajor(),
					protoSemVer.getMinor(),
					protoSemVer.getPatch()
			};
			log.info("Record stream file header is {}", Arrays.toString(fileHeader));
		}
		return fileHeader;
	}
}
