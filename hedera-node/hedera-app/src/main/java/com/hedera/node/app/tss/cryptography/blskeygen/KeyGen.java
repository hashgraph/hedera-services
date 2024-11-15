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

package com.hedera.node.app.tss.cryptography.blskeygen;

import com.hedera.node.app.tss.cryptography.bls.BlsKeyPair;
import com.hedera.node.app.tss.cryptography.bls.BlsPrivateKey;
import com.hedera.node.app.tss.cryptography.bls.BlsPublicKey;
import com.hedera.node.app.tss.cryptography.bls.GroupAssignment;
import com.hedera.node.app.tss.cryptography.bls.SignatureSchema;
import com.hedera.node.app.tss.cryptography.pairings.api.Curve;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Files;

/**
 * Key generation tool
 *
 * <p>Usage:
 *
 * <p>Display usage information:
 *
 * <pre>{@code  --help}</pre>
 *
 * <p>Generating a Key Pair:
 * <pre>{@code generate-keys path/to/privateKey.pem path/to/publicKey.pem}</pre>
 *
 * <p>Generating a Public Key from an Existing Private Key:
 *
 * <pre>{@code generate-public-key path/to/privateKey.pem path/to/publicKey.pem}</pre>
 */
public class KeyGen {
    private static final SignatureSchema SIGNATURE_SCHEMA =
            SignatureSchema.create(Curve.ALT_BN128, GroupAssignment.SHORT_SIGNATURES);

    /**
     * Empty method for static helper class
     */
    private KeyGen() {
        // Empty method for static helper class
    }

    /**
     * <p>Usage:
     *
     * <p>Display usage information:
     *
     * <pre>{@code  --help}</pre>
     *
     * <p>Generating a Key Pair:
     * <pre>{@code generate-keys path/to/privateKey.pem path/to/publicKey.pem}</pre>
     *
     * <p>Generating a Public Key from an Existing Private Key:
     *
     * <pre>{@code generate-public-key path/to/privateKey.pem path/to/publicKey.pem}</pre>
     *
     * @param args depending on the command see examples above
     * @throws Exception if something happened while generating the keys
     */
    public static void main(String[] args) throws Exception {
        final CliArguments cliArguments = CliArguments.parse(args);
        switch (cliArguments.command()) {
            case PRINT_HELP -> printHelpAndExit();
            case GENERATE_KEYS -> {
                if (Files.exists(cliArguments.privateKeyPath())) {
                    error(
                            "The private key file already exists. Won't overwrite. Please delete %s %n",
                            cliArguments.privateKeyPath());
                }
                if (Files.exists(cliArguments.publicKeyPath())) {
                    error(
                            "The public key file already exists. Won't overwrite. Please delete %s %n",
                            cliArguments.publicKeyPath());
                }
                final BlsKeyPair keyPair = BlsKeyPair.generate(SIGNATURE_SCHEMA);
                PemFiles.writeKey(cliArguments.privateKeyPath(), keyPair.privateKey());
                PemFiles.writeKey(cliArguments.publicKeyPath(), keyPair.publicKey());
                System.out.printf(
                        "Saved private and public key files into: %s and %s %n",
                        cliArguments.privateKeyPath(), cliArguments.publicKeyPath());
            }
            case GENERATE_PUBLIC_KEY -> {
                if (!Files.exists(cliArguments.privateKeyPath())) {
                    error("The private key file does not exist. %s %n", cliArguments.privateKeyPath());
                }
                if (Files.exists(cliArguments.publicKeyPath())) {
                    error(
                            "The public key file already exists. Won't overwrite. Please delete %s %n",
                            cliArguments.publicKeyPath());
                }
                final BlsPrivateKey privateKey = PemFiles.readPrivateKey(cliArguments.privateKeyPath());
                final BlsPublicKey publicKey = privateKey.createPublicKey();
                PemFiles.writeKey(cliArguments.publicKeyPath(), publicKey);
                System.out.printf("Saved public key file into: %s %n", cliArguments.publicKeyPath());
            }
        }
    }

    /**
     * Prints an error message and exits
     *
     * @param message     the error message
     * @param messageArgs the message arguments
     */
    private static void error(@NonNull final String message, @NonNull final Object... messageArgs) {
        System.err.printf(message, messageArgs);
        System.exit(1);
    }

    /**
     * Prints the help message and exits
     */
    private static void printHelpAndExit() {
        System.out.println(
                """
                        Usage:
                          --help                           Print this help message.
                          generate-keys <private-key-pem> <public-key-pem>
                                                           Generate a private and public key pair and save them to the specified locations.
                          generate-public-key <private-key-pem> <public-key-pem>
                                                           Generate a public key from a given private key PEM file and save it to the specified location.
                        """);
        System.exit(0);
    }
}
