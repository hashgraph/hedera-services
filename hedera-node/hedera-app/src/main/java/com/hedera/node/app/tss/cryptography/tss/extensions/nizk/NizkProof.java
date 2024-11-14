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

package com.hedera.node.app.tss.cryptography.tss.extensions.nizk;

import com.hedera.node.app.tss.cryptography.bls.SignatureSchema;
import com.hedera.node.app.tss.cryptography.pairings.api.Field;
import com.hedera.node.app.tss.cryptography.pairings.api.FieldElement;
import com.hedera.node.app.tss.cryptography.pairings.api.Group;
import com.hedera.node.app.tss.cryptography.pairings.api.GroupElement;
import com.hedera.node.app.tss.cryptography.utils.HashUtils;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Random;

import static com.hedera.node.app.tss.cryptography.tss.api.TssShareTable.getForShareId;

/**
 * A NizkProof allows a participant to prove that the encrypted values it is sharing are valid
 * Shamir Secret Sharing.
 *
 * @param f  f component of the proof.
 * @param a  a component of the proof.
 * @param y  y component of the proof.
 * @param zR zR component of the proof.
 * @param zA zA component of the proof.
 */
public record NizkProof(
        @NonNull GroupElement f,
        @NonNull GroupElement a,
        @NonNull GroupElement y,
        @NonNull FieldElement zR,
        @NonNull FieldElement zA) {
    /**
     * Creates an instance of a {@link NizkProof}
     *
     * @param f  f component of the proof.
     * @param a  a component of the proof.
     * @param y  y component of the proof.
     * @param zR zR component of the proof.
     * @param zA zA component of the proof.
     */
    public NizkProof {
        Objects.requireNonNull(f, "f must not be null");
        Objects.requireNonNull(a, "a must not be null");
        Objects.requireNonNull(y, " y must not be null");
        Objects.requireNonNull(zR, "zR must not be null");
        Objects.requireNonNull(zA, "zA must not be null");
    }

    /**
     * Generates a NizkProof that proves that the commitments and the encrypted shares correspond to a valid secret sharing according
     * to the polynomial that generated them.
     *
     * @param signatureSchema defines which elliptic curve is used in the protocol, and how it's used
     * @param random a source of randomness
     * @param statement       the public part of the proof
     * @param witness         the private part of the proof
     * @return a {@link NizkProof} proof.
     */
    @NonNull
    public static NizkProof prove(
            @NonNull final SignatureSchema signatureSchema,
            @NonNull final Random random,
            @NonNull final NizkStatement statement,
            @NonNull final NizkWitness witness) {
        final Field field = Objects.requireNonNull(signatureSchema, "signatureSchema must not be null")
                .getPairingFriendlyCurve()
                .field();
        Objects.requireNonNull(random, "random must not be null");
        Objects.requireNonNull(statement, "statement must not be null");
        Objects.requireNonNull(witness, "witness must not be null");

        final Group publicKeyGroup = signatureSchema.getPublicKeyGroup();
        final GroupElement generator = publicKeyGroup.generator();

        // compute x := RO(instance)
        final FieldElement x = field.fromBytes(statement.hash());

        // Generate random α, ρ ←$ Zp
        final FieldElement alpha = field.random(random);
        final FieldElement rho = field.random(random);

        // compute F = g^rho
        final GroupElement f = generator.multiply(rho);
        // compute A = g^alpha
        final GroupElement a = generator.multiply(alpha);

        final List<FieldElement> xPowerId =
                statement.tssShareIds().stream().map(x::power).toList();

        // compute Y = Π_{i=1}^{n} (y_i)^x^i
        GroupElement yAggregator = publicKeyGroup.zero();
        for (int shareId : statement.tssShareIds()) {
            final GroupElement yi =
                    statement.tssEncryptionKeys().getForShareId(shareId).element();
            yAggregator = yAggregator.add(yi.multiply(getForShareId(xPowerId, shareId)));
        }
        final GroupElement y = yAggregator.multiply(rho).add(a);

        // compute x' := RO(x, F, A, Y)
        final byte[] hash = HashUtils.computeHash(HashUtils.SHA256, x.toBytes(), f.toBytes(), a.toBytes(), y.toBytes());
        final FieldElement xPrime = field.random(hash);

        // compute z_r = x' * r + rho
        final FieldElement z_r = xPrime.multiply(witness.randomness()).add(rho);

        FieldElement sigma = field.fromLong(0L);
        for (int shareId : statement.tssShareIds()) {
            final FieldElement si = getForShareId(witness.secrets(), shareId);
            sigma = sigma.add(si.multiply(getForShareId(xPowerId, shareId)));
        }
        // compute z_a = x' * Sigma_{i=1}^{n} (s_i)*x^i + alpha
        final FieldElement z_a = xPrime.multiply(sigma).add(alpha);
        return new NizkProof(f, a, y, z_r, z_a);
    }

    /**
     * Verifies this proof against another statement
     *
     * @param signatureSchema defines which elliptic curve is used in the protocol, and how it's used
     * @param statement       the public information to verify this proof
     * @return true if the statement matches the information used to generate this proof. False otherwise
     */
    public boolean verify(@NonNull final SignatureSchema signatureSchema, @NonNull final NizkStatement statement) {
        final Field field = Objects.requireNonNull(signatureSchema, "signatureSchema must not be null")
                .getPairingFriendlyCurve()
                .field();
        final Group publicKeyGroup = signatureSchema.getPublicKeyGroup();
        // compute x := RO(instance)
        final byte[] statementHash =
                Objects.requireNonNull(statement, "statement must not be null").hash();
        final FieldElement x = field.fromBytes(statementHash);
        // compute x' := RO(x, F, A, Y)
        final byte[] hash = HashUtils.computeHash(
                HashUtils.SHA256, x.toBytes(), this.f.toBytes(), this.a.toBytes(), this.y.toBytes());
        final FieldElement xPrime = field.random(hash);
        GroupElement lhs;
        GroupElement rhs;

        // check R ^ x' . F = g ^ z_r
        lhs = statement.combinedCiphertext().randomness().multiply(xPrime).add(f);
        rhs = publicKeyGroup.generator().multiply(this.zR);
        if (!lhs.equals(rhs)) {
            return false;
        }

        // the list of shares is stored in a way where index 0 belongs to shareId=1,
        // so to retrieve x^1 we need to access shareId-1 index.
        final List<FieldElement> xPowerId =
                statement.tssShareIds().stream().map(x::power).toList();
        final List<Entry<FieldElement, FieldElement>> idxPowerId = statement.tssShareIds().stream()
                .map(id -> Map.entry(field.fromLong(id), getForShareId(xPowerId, id)))
                .toList();
        final List<GroupElement> results = new ArrayList<>();
        final List<GroupElement> polyCoefficients =
                statement.polynomialCommitment().coefficients();
        for (int k = 0; k < polyCoefficients.size(); k++) {
            final GroupElement kthCoefficients = polyCoefficients.get(k);
            final List<FieldElement> list = new ArrayList<>();
            for (var entry : idxPowerId) {
                list.add(entry.getKey().power(k).multiply(entry.getValue()));
            }
            FieldElement fold = field.add(list);
            results.add(kthCoefficients.multiply(fold));
        }

        GroupElement inner = publicKeyGroup.add(results);
        lhs = inner.multiply(xPrime).add(this.a);
        rhs = publicKeyGroup.generator().multiply(this.zA);
        if (!lhs.equals(rhs)) {
            return false;
        }

        // CombinedCipherText are stored per index, so to retrieve them by shareId we need to decrease the value by 1
        inner = publicKeyGroup.zero();
        for (int shareId : statement.tssShareIds()) {
            final GroupElement ci = statement.combinedCiphertext().getForShareId(shareId);
            inner = inner.add(ci.multiply(getForShareId(xPowerId, shareId)));
        }

        lhs = inner.multiply(xPrime).add(this.y);

        inner = publicKeyGroup.zero();
        for (int shareId : statement.tssShareIds()) {
            GroupElement yi =
                    statement.tssEncryptionKeys().getForShareId(shareId).element();
            inner = inner.add(yi.multiply(this.zR.multiply(getForShareId(xPowerId, shareId))));
        }

        rhs = inner.add(publicKeyGroup.generator().multiply(this.zA));
        return lhs.equals(rhs);
    }
}
