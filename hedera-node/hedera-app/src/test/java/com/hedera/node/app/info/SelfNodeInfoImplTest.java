/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.node.app.info;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.system.address.Address;
import com.swirlds.state.spi.info.SelfNodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SelfNodeInfoImplTest {
    private static final NodeId NODE_ID = new NodeId(123);
    private static final String MEMO = "0.0.3";
    private static final String SELF_NAME = "SELF_NAME";
    private static final String EXTERNAL_HOSTNAME = "ext.com";
    private static final String INTERNAL_HOSTNAME = "127.0.0.1";
    private static final long WEIGHT = 321L;
    private static final int INTERNAL_PORT = 456;
    private static final int EXTERNAL_PORT = 789;
    private static final byte[] ENCODED_PUB_KEY = new byte[32];
    private static final byte[] ENCODED_X509_CERT = new byte[256];
    private static final SemanticVersion HAPI_VERSION = new SemanticVersion(1, 2, 3, "alpha.4", "5");

    @Mock
    private Address address;

    @Mock
    private X509Certificate x509Certificate;

    @Mock
    private PublicKey publicKey;

    @BeforeEach
    void setUp() {
        given(address.getSigCert()).willReturn(x509Certificate);
        given(address.getNodeId()).willReturn(NODE_ID);
        given(address.getMemo()).willReturn(MEMO);
        given(address.getWeight()).willReturn(WEIGHT);
        given(address.getHostnameExternal()).willReturn(EXTERNAL_HOSTNAME);
        given(address.getHostnameInternal()).willReturn(INTERNAL_HOSTNAME);
        given(address.getPortInternal()).willReturn(INTERNAL_PORT);
        given(address.getPortExternal()).willReturn(EXTERNAL_PORT);
        given(address.getSigPublicKey()).willReturn(publicKey);
        given(address.getSelfName()).willReturn(SELF_NAME);
        given(publicKey.getEncoded()).willReturn(ENCODED_PUB_KEY);
    }

    @Test
    void usesEmptyBytesForBadCert() throws CertificateEncodingException {
        given(x509Certificate.getEncoded()).willThrow(CertificateEncodingException.class);

        final var subject = SelfNodeInfoImpl.of(address, HAPI_VERSION);

        assertNonSigCertFieldsAsExpected(subject);
        assertEquals(Bytes.EMPTY, subject.sigCertBytes());
    }

    @Test
    void usesEncodedBytesForGoodCert() throws CertificateEncodingException {
        given(x509Certificate.getEncoded()).willReturn(ENCODED_X509_CERT);

        final var subject = SelfNodeInfoImpl.of(address, HAPI_VERSION);

        assertNonSigCertFieldsAsExpected(subject);
        assertEquals(Bytes.wrap(ENCODED_X509_CERT), subject.sigCertBytes());
    }

    private void assertNonSigCertFieldsAsExpected(@NonNull final SelfNodeInfo subject) {
        assertEquals(NODE_ID.id(), subject.nodeId());
        assertEquals(MEMO, subject.memo());
        assertEquals(SELF_NAME, subject.selfName());
        assertEquals(WEIGHT, subject.stake());
        assertEquals(EXTERNAL_HOSTNAME, subject.externalHostName());
        assertEquals(EXTERNAL_PORT, subject.externalPort());
        assertEquals(INTERNAL_HOSTNAME, subject.internalHostName());
        assertEquals(INTERNAL_PORT, subject.internalPort());
        assertEquals(HAPI_VERSION, subject.hapiVersion());
        assertEquals(CommonUtils.hex(ENCODED_PUB_KEY), subject.hexEncodedPublicKey());
        assertEquals(HAPI_VERSION, subject.hapiVersion());
    }
}
