// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.yahcli.commands.clpr;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.clprCompleteConnector;
import static com.hedera.services.yahcli.config.ConfigUtils.configFrom;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.MiscCryptoUtils;
import com.hedera.node.app.hapi.utils.SignatureGenerator;
import com.hedera.node.app.hapi.utils.keys.Ed25519Utils;
import com.hedera.node.app.hapi.utils.keys.Secp256k1Utils;
import com.hedera.services.yahcli.suites.ClprTxnSuite;
import com.hederahashgraph.api.proto.java.ClprSignatureScheme;
import com.hederahashgraph.api.proto.java.Key;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Submits Phase 2 (Reveal) of the two-phase connector registration protocol — a
 * {@code ClprCompleteConnector} HAPI transaction that opens the prior commitment by
 * revealing the connector's id, public key, salt, and reveal signature.
 *
 * <p>For convenience, {@code --identity <path>} loads all of {@code channel-id},
 * {@code connector-id}, {@code public-key}, {@code privateKey}, {@code salt}, and
 * {@code signature-scheme} from a JSON bundle produced by
 * {@link GenerateConnectorIdentityCommand}; any flag passed explicitly overrides the
 * bundled value. The private key signs the fully resolved reveal; an explicit signature
 * takes precedence. Pair with {@link RegisterConnectorCommand} (Phase 1).
 */
@Command(
        name = "complete-connector",
        subcommands = {HelpCommand.class},
        description = "Submits a ClprCompleteConnector (Phase 2: Reveal) transaction. "
                + "Pass --identity <path> to source channel-id, connector-id, public-key, privateKey, salt, "
                + "and signature-scheme from a generate-connector-identity JSON bundle; any individual flag also "
                + "supplied overrides the bundle value.")
public class CompleteConnectorCommand implements Callable<Integer> {

    @ParentCommand
    ClprCommand clprCommand;

    @Option(
            names = {"--identity"},
            paramLabel = "<path>",
            description = "Optional path to a connector identity JSON bundle (from `generate-connector-identity`). "
                    + "Supplies channel-id, connector-id, public-key, privateKey, salt, "
                    + "and --signature-scheme.")
    String identityPath;

    @Option(
            names = {"--connector-id"},
            paramLabel = "<hex>",
            description = "Hex-encoded connector id (32 bytes). Required unless supplied via --identity.")
    String connectorIdHex;

    @Option(
            names = {"--public-key"},
            paramLabel = "<hex>",
            description = "Hex-encoded connector public key bytes. Required unless supplied via --identity.")
    String publicKeyHex;

    @Option(
            names = {"--signature"},
            paramLabel = "<hex>",
            description = "Hex-encoded signature over the canonical reveal payload. "
                    + "Required unless --identity supplies a private key or a signature for this exact payload.")
    String signatureHex;

    @Option(
            names = {"--signature-scheme"},
            paramLabel = "<scheme>",
            description = "One of: ED25519, ECDSA_SECP256K1. Defaults to ED25519 (or the bundle's value).")
    String signatureScheme;

    @Option(
            names = {"--salt"},
            paramLabel = "<hex>",
            description =
                    "Hex-encoded salt used in the commitment preimage. " + "Required unless supplied via --identity.")
    String saltHex;

    @Option(
            names = {"--channel-id"},
            paramLabel = "<hex>",
            description = "Hex-encoded channel id (32 bytes) this connector serves. "
                    + "Required unless supplied via --identity.")
    String channelIdHex;

    @Option(
            names = {"--connector-contract"},
            paramLabel = "<shard.realm.num>",
            required = true,
            description = "Connector contract id (e.g. the PassThroughAuth deployment on this ledger). Required: "
                    + "ClprCompleteConnectorHandler.pureChecks rejects transactions missing a connector contract.")
    String connectorContract;

    @Option(
            names = {"--admin-key"},
            paramLabel = "<hex>",
            description = "Hex-encoded public key to set as the connector's admin key. Scheme is inferred from "
                    + "length: 32 bytes = ED25519; 33 bytes = ECDSA_SECP256K1 compressed; 64 bytes = "
                    + "ECDSA_SECP256K1 uncompressed (X||Y, no 0x04 header, compressed on your behalf). "
                    + "Defaults to the connector's own --public-key (so the operator that controls the connector "
                    + "keypair is also its administrator). Mutually exclusive with --admin-key-file.")
    String adminKeyHex;

    @Option(
            names = {"--admin-key-file"},
            paramLabel = "<path>",
            description = "Path to a file containing a serialized proto.Key protobuf for the connector's admin key. "
                    + "Use this for non-ED25519 keys or pre-built proto.Key values; otherwise --admin-key is simpler.")
    String adminKeyFile;

    @Option(
            names = {"--locked-stake"},
            paramLabel = "<tinybars>",
            defaultValue = "0",
            description = "Locked stake (tinybars) for the connector")
    long lockedStake;

    @Override
    public Integer call() throws Exception {
        final var config = configFrom(clprCommand.getYahcli());

        final Map<String, String> bundle = (identityPath != null && !identityPath.isBlank())
                ? ClprArgs.readFlatJsonStringFields(Path.of(identityPath))
                : Map.of();

        final var channelIdValue = pickFirst(channelIdHex, bundle.get("channelId"));
        final var connectorIdValue = pickFirst(connectorIdHex, bundle.get("connectorId"));
        final var publicKeyValue = pickFirst(publicKeyHex, bundle.get("publicKey"));
        final var signatureValue = pickFirst(signatureHex, bundle.get("signature"));
        final var saltValue = pickFirst(saltHex, bundle.get("salt"));
        final var schemeValue = normalizeScheme(pickFirst(signatureScheme, bundle.get("signatureScheme"), "ED25519"));

        final var op = clprCompleteConnector()
                .connectorId(ClprArgs.requiredBytes("connector-id", connectorIdValue))
                .publicKey(ClprArgs.requiredBytes("public-key", publicKeyValue))
                .signatureScheme(ClprSignatureScheme.valueOf(schemeValue))
                .salt(ClprArgs.requiredBytes("salt", saltValue))
                .channelId(ClprArgs.requiredBytes("channel-id", channelIdValue))
                .lockedStake(lockedStake);
        if (connectorContract != null && !connectorContract.isBlank()) {
            op.connectorContractId(ClprArgs.parseContractId(connectorContract));
        }
        final var adminKeyHexProvided = adminKeyHex != null && !adminKeyHex.isBlank();
        final var adminKeyFileProvided = adminKeyFile != null && !adminKeyFile.isBlank();
        if (adminKeyHexProvided && adminKeyFileProvided) {
            throw new IllegalArgumentException("Cannot specify both --admin-key and --admin-key-file; pick one.");
        }
        if (adminKeyHexProvided) {
            op.adminKey(buildAdminKey(ClprArgs.parseHex(adminKeyHex)));
        } else if (adminKeyFileProvided) {
            final var keyBytes = ClprArgs.readBytesFile(Path.of(adminKeyFile));
            op.adminKey(Key.parseFrom(keyBytes));
        } else {
            // Default: the connector's own signing public key doubles as its admin key. This
            // matches the common case where one operator controls both the connector's identity
            // and its administration. The bytes are guaranteed present here because publicKey
            // is required for the reveal itself.
            final var connectorPubKey = ClprArgs.requiredBytes("public-key", publicKeyValue);
            op.adminKey(defaultAdminKey(schemeValue, connectorPubKey));
        }

        if (signatureHex != null && !signatureHex.isBlank()) {
            op.signature(ClprArgs.requiredBytes("signature", signatureHex));
        } else if (bundle.get("privateKey") != null) {
            op.signatureSigner(
                    identitySigner(schemeValue, ClprArgs.requiredBytes("privateKey", bundle.get("privateKey"))));
        } else {
            op.signature(ClprArgs.requiredBytes("signature", signatureValue));
        }

        final var delegate = new ClprTxnSuite(config, "ClprCompleteConnector", op);
        delegate.runSuiteSync();
        return ClprOutcome.reportTxn(
                config, delegate, op, "completed CLPR connector reveal", "could not complete CLPR connector reveal");
    }

    static Function<byte[], byte[]> identitySigner(final String scheme, final byte[] keyBytes) {
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("Connector private key must be 32 bytes");
        }
        final PrivateKey privateKey =
                switch (scheme) {
                    case "ED25519" -> Ed25519Utils.keyFrom(keyBytes);
                    case "ECDSA_SECP256K1" -> Secp256k1Utils.readECKeyFrom(keyBytes);
                    default -> throw new IllegalArgumentException("Unsupported signature scheme: " + scheme);
                };
        return digest -> {
            try {
                return SignatureGenerator.signBytes(digest, privateKey);
            } catch (final GeneralSecurityException e) {
                throw new IllegalStateException("Unable to sign connector reveal", e);
            }
        };
    }

    /**
     * Upper-cases the scheme so {@link ClprSignatureScheme#valueOf} and the admin-key
     * switch accept lowercase user input (e.g. {@code --signature-scheme ecdsa_secp256k1}).
     */
    private static String normalizeScheme(final String scheme) {
        if (scheme == null || scheme.isBlank()) {
            throw new IllegalArgumentException("--signature-scheme is required");
        }
        return scheme.toUpperCase();
    }

    /**
     * Builds a {@code proto.Key} from raw admin-key bytes, inferring the scheme from length:
     * 32 → ED25519, 33 → ECDSA_SECP256K1 compressed (used directly), 64 → ECDSA_SECP256K1
     * uncompressed (X||Y, no 0x04 header — compressed on the user's behalf).
     */
    private static Key buildAdminKey(final byte[] keyBytes) {
        return switch (keyBytes.length) {
            case 32 ->
                Key.newBuilder().setEd25519(ByteString.copyFrom(keyBytes)).build();
            case 33 ->
                Key.newBuilder()
                        .setECDSASecp256K1(ByteString.copyFrom(keyBytes))
                        .build();
            case 64 ->
                Key.newBuilder()
                        .setECDSASecp256K1(ByteString.copyFrom(MiscCryptoUtils.compressSecp256k1(keyBytes)))
                        .build();
            default ->
                throw new IllegalArgumentException(
                        "--admin-key must be 32 (ED25519), 33 (ECDSA compressed), or 64 (ECDSA uncompressed) bytes; got "
                                + keyBytes.length);
        };
    }

    /**
     * Default admin key derived from the connector's own reveal public key, matching the
     * connector's signature scheme so the operator that controls the connector keypair is
     * also its administrator.
     */
    private static Key defaultAdminKey(final String schemeValue, final byte[] connectorPubKey) {
        return switch (schemeValue) {
            case "ED25519" -> {
                if (connectorPubKey.length != 32) {
                    throw new IllegalArgumentException(
                            "ED25519 connector public key must be 32 bytes (got " + connectorPubKey.length + ")");
                }
                yield Key.newBuilder()
                        .setEd25519(ByteString.copyFrom(connectorPubKey))
                        .build();
            }
            case "ECDSA_SECP256K1" -> {
                if (connectorPubKey.length != 64) {
                    throw new IllegalArgumentException("ECDSA_SECP256K1 connector public key must be 64 bytes "
                            + "(uncompressed X||Y, no 0x04 header); got " + connectorPubKey.length);
                }
                yield Key.newBuilder()
                        .setECDSASecp256K1(ByteString.copyFrom(MiscCryptoUtils.compressSecp256k1(connectorPubKey)))
                        .build();
            }
            default -> throw new IllegalArgumentException("Unsupported --signature-scheme '" + schemeValue + "'");
        };
    }

    /** Returns the first non-null, non-blank value in {@code values}, or {@code null} if all are blank. */
    @Nullable
    private static String pickFirst(@Nullable final String... values) {
        if (values == null) return null;
        for (final var v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
