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

package com.hedera.node.app.tss.cryptography.tss.extensions.elgamal;

import com.hedera.node.app.tss.cryptography.bls.BlsPrivateKey;
import com.hedera.node.app.tss.cryptography.bls.BlsPublicKey;
import com.hedera.node.app.tss.cryptography.bls.SignatureSchema;
import com.hedera.node.app.tss.cryptography.pairings.api.Field;
import com.hedera.node.app.tss.cryptography.pairings.api.FieldElement;
import com.hedera.node.app.tss.cryptography.pairings.api.Group;
import com.hedera.node.app.tss.cryptography.pairings.api.GroupElement;
import com.hedera.node.app.tss.cryptography.pairings.extensions.FiniteFieldPolynomial;
import com.hedera.node.app.tss.cryptography.tss.api.TssException;
import com.hedera.node.app.tss.cryptography.tss.api.TssShareTable;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * A utility class for performing ElGamal encryption and decryption operations.
 *
 * <p>This class provides methods to create ElGamal-encrypted ciphertext chunks and decrypt them using preprocessed substitution tables.
 * It also provides utility methods for generating these substitution tables for encryption and decryption.
 */
public class ElGamalUtils {

    /**
     * Private constructor for static access
     */
    private ElGamalUtils() {}

    /**
     * 256
     */
    public static final int TOTAL_NUMBER_OF_ELEMENTS = -Byte.MIN_VALUE + Byte.MAX_VALUE + 1;

    /**
     * Creates an ElGamal ciphertext from a {@code byte[]} value, chunking it byte by byte, and encrypting each chunk.
     *
     * <p>The method divides the input {@code value} into byte-sized chunks, then encrypts each chunk using the
     * provided {@code encryptionPublicKey}, the preprocessed {@code elGamalDirectSubstitutionTable}, and the
     * provided {@code randomness}.
     *
     *
     * @param encryptionPublicKey the public key used for encryption
     * @param elGamalDirectSubstitutionTable a preprocessed substitution table mapping byte values to {@link FieldElement} values
     * @param randomness a list of random {@link FieldElement} used for ciphertext generation
     * @param value the byte array to encrypt
     * @return a list of {@link GroupElement} representing the encrypted ciphertext chunks
     * @throws IllegalArgumentException if the size of {@code randomness} does not match the length of the {@code value}
     */
    @NonNull
    public static CipherText createCipherText(
            @NonNull final BlsPublicKey encryptionPublicKey,
            @NonNull final ElGamalSubstitutionTable<FieldElement, Byte> elGamalDirectSubstitutionTable,
            @NonNull final List<FieldElement> randomness,
            @NonNull final byte[] value) {

        if (randomness.isEmpty() || randomness.size() != value.length) {
            throw new IllegalArgumentException("Invalid randomness size");
        }

        final GroupElement encryptionPublicKeyElement = encryptionPublicKey.element();
        final Group group = encryptionPublicKeyElement.getGroup();
        final GroupElement generator = group.generator();

        final List<GroupElement> encryptedShareElements = new ArrayList<>();
        for (int i = 0; i < value.length; i++) {
            final FieldElement r_j = randomness.get(i);
            final FieldElement m_j = elGamalDirectSubstitutionTable.get(value[i]);
            if (m_j == null) {
                // This should never happen: It means that the ElGamalSubstitutionTable is incorrectly configured
                throw new TssException("Wrong ElGamalSubstitutionTable");
            }
            final GroupElement c2_j = encryptionPublicKeyElement.multiply(r_j).add(generator.multiply(m_j));
            encryptedShareElements.add(c2_j);
        }

        return new CipherText(encryptedShareElements);
    }

    /**
     * Decrypts a list of ElGamal-encrypted ciphertext chunks to recover the original byte array value using brute force.
     *<p>
     * In cases input parameters not matching the ones that produced the encrypted values, or dishonest decryption attempt,
     * this method will either provide an invalid byte[] as result or return null, with no guarantees of which.
     * <p><strong>Note:</strong> It is the responsibility of the caller to ensure compatibility between curve and groups.
     * All elements must belong to the same configuration.
     *
     * @param decryptionPrivateKey the private key used for decryption
     * @param cipherText the cipherText containing a list of {@link GroupElement} representing the ciphertext chunks to decrypt
     * @param elGamalInverseSubstitutionTable the preprocessed inverse substitution table used for brute-force decryption
     * @param randomness the list of random {@link GroupElement} values used during encryption
     * @return the decrypted byte array in honest decryption attempts, no guarantees of the obtained result otherwise.
     * @throws IllegalArgumentException if the size of {@code randomness} does not match the size of {@code cipherTextElements}
     * @throws NullPointerException if any of the parameters is null
     * @implNote This method performs decryption by using the {@code decryptionPrivateKey} and the preprocessed {@code elGamalInverseSubstitutionTable}
     * to convert each encrypted chunk back to its original value. It uses the provided {@code randomness} to unmask each ciphertext chunk
     * during the process.
     */
    @Nullable
    public static byte[] readCipherText(
            @NonNull final BlsPrivateKey decryptionPrivateKey,
            @NonNull final List<GroupElement> randomness,
            @NonNull final ElGamalSubstitutionTable<Byte, GroupElement> elGamalInverseSubstitutionTable,
            @NonNull final CipherText cipherText) {

        Objects.requireNonNull(decryptionPrivateKey, "decryptionPrivateKey must not be null");
        if (Objects.requireNonNull(randomness, "randomness must not be null").size()
                != Objects.requireNonNull(cipherText, "cipherText must not be null")
                        .size()) {
            throw new IllegalArgumentException("Mismatched randomness and ciphertext size");
        }
        Objects.requireNonNull(elGamalInverseSubstitutionTable, "elGamalInverseSubstitutionTable must not be null");

        final FieldElement keyElement = decryptionPrivateKey.element();
        final Field keyField = keyElement.field();

        final FieldElement zeroElement = keyField.fromLong(0L);
        final List<GroupElement> cipherTextElements = cipherText.cipherText();

        final byte[] output = new byte[cipherTextElements.size()];
        for (int i = 0; i < cipherTextElements.size(); i++) {
            final GroupElement chunkCiphertext = cipherTextElements.get(i);
            final GroupElement chunkRandomness = randomness.get(i);
            final GroupElement antiMask = chunkRandomness.multiply(zeroElement.subtract(keyElement));
            final GroupElement commitment = chunkCiphertext.add(antiMask);

            final Byte value = elGamalInverseSubstitutionTable.get(commitment);
            if (value == null) {
                return null;
            }
            output[i] = value;
        }

        return output;
    }

    /**
     * Generates an inverse substitution table used to map a {@link GroupElement} to its corresponding byte value.
     *
     * <p>This map is used during decryption to obtain the original byte value from a group element generated by multiplying
     * the group generator by the byte value.
     *
     * @param signatureSchema defines which elliptic curve is used in the protocol, and how it's used
     * @return a map of {@link GroupElement} to byte values used for decryption
     */
    @NonNull
    public static ElGamalSubstitutionTable<Byte, GroupElement> elGamalReverseSubstitutionTable(
            @NonNull final SignatureSchema signatureSchema) {
        return ElGamalSubstitutionTable.inverse(signatureSchema);
    }

    /**
     * Generates a substitution table mapping byte values to their corresponding {@link FieldElement} values.
     *
     * <p>This map is used during encryption to substitute byte values with corresponding field elements.
     *
     * @param signatureSchema defines which elliptic curve is used in the protocol, and how it's used
     * @return a map of byte values to {@link FieldElement} used for encryption
     */
    @NonNull
    public static ElGamalSubstitutionTable<FieldElement, Byte> elGamalSubstitutionTable(
            @NonNull final SignatureSchema signatureSchema) {
        return ElGamalSubstitutionTable.direct(signatureSchema);
    }

    /**
     * Generates randomness consisting of length number of {@link FieldElement}s
     *
     * @param random a source of randomness
     * @param length The length of the resulting list
     * @param signatureSchema defines which elliptic curve is used in the protocol, and how it's used
     * @return a list of random field elements
     */
    @NonNull
    public static List<FieldElement> generateEntropy(
            @NonNull final Random random, final int length, @NonNull final SignatureSchema signatureSchema) {
        Objects.requireNonNull(random, "random must not be null");
        final Field field = Objects.requireNonNull(signatureSchema, "signatureSchema must not be null")
                .getPairingFriendlyCurve()
                .field();
        final List<FieldElement> randomness = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            randomness.add(field.random(random));
        }
        return List.copyOf(randomness);
    }

    /**
     * Combines randomness field elements into a single field element.
     *
     * @param fieldRandomness the randomness field elements
     * @return the combined randomness element
     */
    public static FieldElement combineFieldRandomness(@NonNull final List<FieldElement> fieldRandomness) {
        if (Objects.requireNonNull(fieldRandomness, "fieldRandomness must not be null")
                .isEmpty()) {
            throw new IllegalArgumentException("fieldRandomness must not be empty");
        }
        return new FiniteFieldPolynomial(fieldRandomness).evaluate(TOTAL_NUMBER_OF_ELEMENTS);
    }

    /**
     * Creates a {@link CiphertextTable} from two coordinate lists of tssShareIds and secret value.
     *
     * @param signatureSchema defines which elliptic curve is used in the protocol, and how it's used
     * @param randomness to use for ElGamal encryption.
     * @param tssEncryptionKeyResolver a resolver to retrieve each participant's owner's encryption key
     * @param secrets the unencrypted messages to encrypt
     * @return a {@link CiphertextTable}
     */
    public static CiphertextTable ciphertextTable(
            @NonNull final SignatureSchema signatureSchema,
            @NonNull final List<FieldElement> randomness,
            @NonNull final TssShareTable<BlsPublicKey> tssEncryptionKeyResolver,
            @NonNull final List<FieldElement> secrets) {

        final Group publicKeyGroup = Objects.requireNonNull(signatureSchema, "signatureSchema must not be null")
                .getPublicKeyGroup();
        final GroupElement publicKeyGenerator = publicKeyGroup.generator();
        final ElGamalSubstitutionTable<FieldElement, Byte> elGamalSubstitutionTable =
                ElGamalUtils.elGamalSubstitutionTable(signatureSchema);
        Objects.requireNonNull(tssEncryptionKeyResolver, "tssEncryptionKeyResolver must not be null");

        final List<GroupElement> chunkRandomness = new ArrayList<>();
        for (final FieldElement randomElement : randomness) {
            chunkRandomness.add(publicKeyGenerator.multiply(randomElement));
        }

        CipherText[] multiEncryptedValues = new CipherText[secrets.size()];
        for (int i = 0; i < secrets.size(); i++) {
            final FieldElement secret = secrets.get(i);
            final BlsPublicKey pk = tssEncryptionKeyResolver.getForShareId(i + 1);
            multiEncryptedValues[i] = createCipherText(pk, elGamalSubstitutionTable, randomness, secret.toBytes());
        }
        return new CiphertextTable(List.copyOf(chunkRandomness), multiEncryptedValues);
    }
}
