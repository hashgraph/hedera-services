/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.crypto;

import static com.swirlds.common.crypto.SignatureType.RSA;
import static com.swirlds.common.utility.CommonUtils.hex;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Objects;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Encapsulates a cryptographic signature along with its SignatureType.
 */
public class Signature implements SelfSerializable {
    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(Signature.class);

    /** a unique class type identifier */
    private static final long CLASS_ID = 0x13dc4b399b245c69L;

    /** the current serialization version */
    private static final int CLASS_VERSION = 1;

    /** The type of cryptographic algorithm used to create the signature */
    private SignatureType signatureType;

    /** signature byte array */
    private byte[] signatureBytes;

    /**
     * For RuntimeConstructable
     */
    public Signature() {}

    public Signature(final SignatureType signatureType, final byte[] signatureBytes) {
        this.signatureType = signatureType;
        this.signatureBytes = signatureBytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        this.signatureType = SignatureType.from(in.readInt(), RSA);
        this.signatureBytes = in.readByteArray(this.signatureType.signatureLength(), true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeInt(signatureType.ordinal());
        out.writeByteArray(signatureBytes, true);
    }

    /**
     * Get the bytes of this signature.
     */
    public byte[] getSignatureBytes() {
        return signatureBytes;
    }

    /**
     * check whether this signature is signed by given publicKey on given data
     *
     * @param data
     * 		the data that was signed
     * @param publicKey
     * 		publicKey
     * @return true if the signature is valid
     */
    public boolean verifySignature(final byte[] data, final PublicKey publicKey) {
        if (publicKey == null) {
            logger.info(EXCEPTION.getMarker(), "PublicKey is missing");
            return false;
        }

        final String signingAlgorithm = signatureType.signingAlgorithm();
        final String sigProvider = signatureType.provider();
        try {
            final java.security.Signature sig = java.security.Signature.getInstance(signingAlgorithm, sigProvider);
            sig.initVerify(publicKey);
            sig.update(data);
            return sig.verify(signatureBytes);
        } catch (final NoSuchAlgorithmException
                | NoSuchProviderException
                | InvalidKeyException
                | SignatureException e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    () -> "Failed to verify Signature: %s, PublicKey: %s".formatted(this, hex(publicKey.getEncoded())),
                    e);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return CLASS_VERSION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof final Signature signature)) {
            return false;
        }

        return Arrays.equals(signatureBytes, signature.signatureBytes) && signatureType == signature.signatureType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(signatureType, Arrays.hashCode(signatureBytes));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .append("signatureType", signatureType)
                .append("sigBytes", hex(signatureBytes))
                .toString();
    }
}
