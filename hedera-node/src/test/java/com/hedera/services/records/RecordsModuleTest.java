package com.hedera.services.records;

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;

import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RecordsModuleTest {
	private final AccountID nodeAccount = IdUtils.asAccount("0.0.3");
	private final String unscopedDirNoSeparator = "just/a/test";
	private final String unscopedDirWithSeparator = "just/a/test" + File.separator;

	private final String expectedScopedDir = unscopedDirWithSeparator + "record0.0.3";

	@Mock
	private NodeLocalProperties nodeLocalProperties;

	@Test
	void worksWithNoSeparator() {
		given(nodeLocalProperties.recordLogDir()).willReturn(unscopedDirNoSeparator);

		// when:
		final var actual = RecordsModule.provideRecordLogDir(nodeLocalProperties, nodeAccount);

		// then:
		Assertions.assertEquals(expectedScopedDir, actual);
	}

	@Test
	void worksWithSeparator() {
		given(nodeLocalProperties.recordLogDir()).willReturn(unscopedDirWithSeparator);

		// when:
		final var actual = RecordsModule.provideRecordLogDir(nodeLocalProperties, nodeAccount);

		// then:
		Assertions.assertEquals(expectedScopedDir, actual);
	}
}