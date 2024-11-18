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

package com.hedera.node.app.service.addressbook.impl.test;

import static com.hedera.node.app.service.addressbook.AddressBookHelper.loadResourceFile;
import static com.hedera.node.app.service.addressbook.AddressBookHelper.readCertificatePemFile;
import static com.hedera.node.app.service.addressbook.AddressBookHelper.writeCertificatePemFile;
import static com.hedera.node.app.service.addressbook.impl.validators.AddressBookValidator.validateX509Certificate;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CertificatePemTest {
    @TempDir
    private File tmpDir;

    @Test
    void validateGeneratedPemFile() throws IOException, CertificateException {
        final var pemFileName = "s-public-node1.pem";
        final var pemFilePath = loadResourceFile(pemFileName);
        final var cert = readCertificatePemFile(pemFilePath);
        final var genPemPath = Path.of(tmpDir.getPath() + "/generated.pem");
        writeCertificatePemFile(genPemPath, cert.getEncoded());
        final var genCert = readCertificatePemFile(genPemPath);

        assertEquals("SHA384withRSA", cert.getSigAlgName());
        assertEquals("X.509", cert.getType());
        assertArrayEquals(cert.getEncoded(), genCert.getEncoded());
        assertEquals(cert, genCert);
        assertDoesNotThrow(() -> validateX509Certificate(Bytes.wrap(genCert.getEncoded())));
    }

    @Test
    void onlyCertificatePemAllowedToRead() {
        final var pemFileName = "s-private-node1.pem";
        final var pemFilePath = loadResourceFile(pemFileName);
        final var exception = assertThrows(CertificateException.class, () -> readCertificatePemFile(pemFilePath));
        assertEquals("Not X509 Certificate, it is PrivateKeyInfo", exception.getMessage());
    }

    @Test
    void invalidCertificatePem() throws CertificateException, IOException {
        final var pemFileName = "s-public-node1.pem";
        final var pemFilePath = loadResourceFile(pemFileName);
        final var cert = readCertificatePemFile(pemFilePath);
        final var test = Path.of(tmpDir.getPath() + "/test");
        Files.write(test, cert.getEncoded());
        final var genCert = readCertificatePemFile(test);
        assertNull(genCert);
    }

    @Test
    void invalidBytesInPemCannotRead() throws IOException {
        final var genPemPath = Path.of(tmpDir.getPath() + "/generated.pem");
        writeCertificatePemFile(genPemPath, Bytes.wrap("anyString").toByteArray());
        final var exception = assertThrows(CertificateException.class, () -> readCertificatePemFile(genPemPath));
        assertThat(exception.getMessage()).contains("Can not read the certificate from the file ");
        final var msg = assertThrows(PreCheckException.class, () -> validateX509Certificate(Bytes.wrap("anyString")));
        assertEquals(ResponseCodeEnum.INVALID_GOSSIP_CA_CERTIFICATE, msg.responseCode());
    }

    @Test
    void badX509CertificateFailedOnReadAndValidation() throws IOException {
        final var pemFileName = "badX509certificate.pem";
        final var pemFilePath = loadResourceFile(pemFileName);
        final var exception = assertThrows(CertificateException.class, () -> readCertificatePemFile(pemFilePath));
        assertEquals("Can not read the certificate from the file badX509certificate.pem", exception.getMessage());

        final byte[] certBytes = Files.readAllBytes(pemFilePath);
        final var msg = assertThrows(PreCheckException.class, () -> validateX509Certificate(Bytes.wrap(certBytes)));
        assertEquals(ResponseCodeEnum.INVALID_GOSSIP_CA_CERTIFICATE, msg.responseCode());
    }

    @Test
    void goodX509CertificateSuccessOnReadAndValidation() throws IOException, CertificateException {
        final var pemFileName = "goodX509certificate.pem";
        final var pemFilePath = loadResourceFile(pemFileName);
        final var cert = readCertificatePemFile(pemFilePath);

        assertEquals("SHA384withRSA", cert.getSigAlgName());
        assertEquals("X.509", cert.getType());
        assertDoesNotThrow(() -> validateX509Certificate(Bytes.wrap(cert.getEncoded())));

        final byte[] certBytes = Files.readAllBytes(pemFilePath);
        assertDoesNotThrow(() -> validateX509Certificate(Bytes.wrap(certBytes)));
    }
}
