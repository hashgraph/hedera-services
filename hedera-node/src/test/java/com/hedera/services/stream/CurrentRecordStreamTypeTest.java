package com.hedera.services.stream;

import com.hedera.services.context.properties.ActiveVersions;
import com.hedera.services.context.properties.SemanticVersions;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

@ExtendWith({ MockitoExtension.class, LogCaptureExtension.class })
class CurrentRecordStreamTypeTest {
	private static final SemanticVersion pretendSemVer = SemanticVersion.newBuilder()
			.setMajor(1)
			.setMinor(2)
			.setPatch(4)
			.setPre("zeta.123")
			.setBuild("2b26be40")
			.build();
	private static final int[] expectedHeader = new int[] {
			RecordStreamType.RECORD_VERSION, pretendSemVer.getMajor(), pretendSemVer.getMinor(),
			pretendSemVer.getPatch()
	};

	@Mock
	private ActiveVersions activeVersions;
	@Mock
	private SemanticVersions semanticVersions;

	@LoggingSubject
	private CurrentRecordStreamType subject;
	@LoggingTarget
	private LogCaptor logCaptor;

	@BeforeEach
	void setUp() {
		subject = new CurrentRecordStreamType(semanticVersions);
	}

	@Test
	void returnsCurrentStreamTypeFromResource() {
		given(semanticVersions.getDeployed()).willReturn(activeVersions);
		given(activeVersions.protoSemVer()).willReturn(pretendSemVer);

		final var header = subject.getFileHeader();
		assertArrayEquals(expectedHeader, header);
		assertSame(header, subject.getFileHeader());
	}

	@Test
	void failsFastIfProtoVersionWasNotLoadedFromResource() {
		given(semanticVersions.getDeployed()).willReturn(activeVersions);
		given(activeVersions.protoSemVer()).willReturn(SemanticVersion.getDefaultInstance());

		subject.getFileHeader();

		assertThat(logCaptor.errorLogs(), contains(Matchers.startsWith("Failed to load")));
	}
}