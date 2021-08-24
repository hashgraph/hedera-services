package com.hedera.services;

import com.hedera.services.state.initialization.SystemFilesManager;
import com.hedera.services.state.logic.NetworkCtxManager;
import com.hedera.services.utils.NamedDigestFactory;
import com.hedera.services.utils.SystemExits;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.function.Supplier;

import static com.hedera.services.context.AppsManager.APPS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ServicesMainAppTest {
	private final long selfId = 123L;
	private final NodeId nodeId = new NodeId(false, selfId);

	@Mock
	private Platform platform;
	@Mock
	private SystemExits systemExits;
	@Mock
	private Supplier<Charset> nativeCharset;
	@Mock
	private ServicesApp app;
	@Mock
	private NamedDigestFactory namedDigestFactory;
	@Mock
	private SystemFilesManager systemFilesManager;
	@Mock
	private NetworkCtxManager networkCtxManager;

	private ServicesMain subject = new ServicesMain();

	@Test
	void failsOnWrongNativeCharset() {
		withDoomedApp();

		given(nativeCharset.get()).willReturn(StandardCharsets.US_ASCII);

		// when:
		subject.init(platform, nodeId);

		// then:
		verify(systemExits).fail(1);
	}

	@Test
	void failsOnUnavailableDigest() throws NoSuchAlgorithmException {
		withDoomedApp();

		given(nativeCharset.get()).willReturn(UTF_8);
		given(namedDigestFactory.forName("SHA-384")).willThrow(NoSuchAlgorithmException.class);
		given(app.digestFactory()).willReturn(namedDigestFactory);

		// when:
		subject.init(platform, nodeId);

		// then:
		verify(systemExits).fail(1);
	}

	@Test
	void doesAppDrivenInit() throws NoSuchAlgorithmException {
		withRunnableApp();

		// when:
		subject.init(platform, nodeId);

		// then:
		verify(systemFilesManager).createAddressBookIfMissing();
		verify(systemFilesManager).createNodeDetailsIfMissing();
		verify(systemFilesManager).createUpdateZipFileIfMissing();
		verify(networkCtxManager).loadObservableSysFilesIfNeeded();
	}

	private void withDoomedApp() {
		APPS.init(selfId, app);
		given(app.nativeCharset()).willReturn(nativeCharset);
		given(app.systemExits()).willReturn(systemExits);
	}

	private void withRunnableApp() throws NoSuchAlgorithmException {
		APPS.init(selfId, app);
		given(nativeCharset.get()).willReturn(UTF_8);
		given(namedDigestFactory.forName("SHA-384")).willReturn(null);
		given(app.nativeCharset()).willReturn(nativeCharset);
		given(app.digestFactory()).willReturn(namedDigestFactory);
		given(app.sysFilesManager()).willReturn(systemFilesManager);
		given(app.networkCtxManager()).willReturn(networkCtxManager);
	}
}