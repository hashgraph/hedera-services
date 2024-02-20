package com.hedera.node.app.records.streams.impl.producers.formats.v1;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.streams.*;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.stream.Signer;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The SignatureWriterV1 is responsible for writing the signature to the file. Once we are able to prove the address
 * book, we should deprecate this class and instead produce a proof directly in the block.
 */
public class SignatureWriterV1 {

    /** Logger to use */
    private static final Logger logger = LogManager.getLogger(SignatureWriterV1.class);
    /** The suffix added to RECORD_EXTENSION for the block signature files */
    private static final String RECORD_SIG_EXTENSION_SUFFIX = "_sig";

    /** The suffix added to RECORD_EXTENSION for the block signature files */
    private static final String BLOCK_SIG_EXTENSION_SUFFIX = "_sig";

    /**
     * Write a signature file for a block file.
     *
     * @param blockFilePath the path to the block file
     * @param signer the signer
     * @param writeMetadataSignature whether to write the metadata signature
     * @param blockFileVersion the block file version
     * @param hapiProtoVersion the hapi proto version
     * @param blockNumber the block number
     * @param startRunningHash the start running hash
     * @param endRunningHash the end running hash
     */
    static void writeSignatureFile(
            @NonNull final Path blockFilePath,
            @NonNull final Signer signer,
            final boolean writeMetadataSignature,
            final int blockFileVersion,
            final SemanticVersion hapiProtoVersion,
            final long blockNumber,
            @NonNull final Bytes startRunningHash,
            @NonNull final Bytes endRunningHash) {
        // write signature file
        final var sigFilePath = getSigFilePath(blockFilePath);
        try (final var fileOut = Files.newOutputStream(sigFilePath, StandardOpenOption.CREATE_NEW)) {
            final var streamingData = new WritableStreamingData(fileOut);
            Bytes metadataHash = null;
            if (writeMetadataSignature) {
                // create metadata hash
                HashingOutputStream hashingOutputStream =
                        new HashingOutputStream(MessageDigest.getInstance(DigestType.SHA_384.algorithmName()));
                SerializableDataOutputStream dataOutputStream = new SerializableDataOutputStream(hashingOutputStream);
                dataOutputStream.writeInt(blockFileVersion);
                dataOutputStream.writeInt(hapiProtoVersion.major());
                dataOutputStream.writeInt(hapiProtoVersion.minor());
                dataOutputStream.writeInt(hapiProtoVersion.patch());
                startRunningHash.writeTo(dataOutputStream);
                endRunningHash.writeTo(dataOutputStream);
                dataOutputStream.writeLong(blockNumber);
                dataOutputStream.close();
                metadataHash = Bytes.wrap(hashingOutputStream.getDigest());
            }
            // create signature file
            final var signatureFile = new SignatureFile(
                    generateSignatureObject(signer, endRunningHash),
                    writeMetadataSignature ? generateSignatureObject(signer, metadataHash) : null);
            // write version in signature file. It is only 1 byte, compared to 4 in block files
            streamingData.writeByte((byte) 6);
            // write protobuf SignatureFile
            SignatureFile.PROTOBUF.write(signatureFile, streamingData);
            logger.debug("signature file saved: {}", sigFilePath);
            // flush
            fileOut.flush();
        } catch (final IOException e) {
            logger.error("Fail to generate signature file for {}", blockFilePath, e);
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Given a hash produce the HAPI signature object for that hash.
     *
     * @param signer The signer to use
     * @param hash The hash to sign
     * @return Signature object
     */
    private static SignatureObject generateSignatureObject(@NonNull final Signer signer, @NonNull final Bytes hash) {
        final Bytes signature = Bytes.wrap(signer.sign(hash.toByteArray()).getSignatureBytes());
        return SignatureObject.newBuilder()
                .type(SignatureType.SHA_384_WITH_RSA)
                .length((int) signature.length())
                .checksum(101 - (int) signature.length()) // simple checksum to detect if at wrong place in
                .signature(signature)
                .hashObject(new HashObject(HashAlgorithm.SHA_384, (int) hash.length(), hash))
                .build();
    }

    /**
     * Get the signature file path for a block file.
     *
     * @param blockFilePath the path to the block file
     * @return the path to the signature file
     */
    private static Path getSigFilePath(@NonNull final Path blockFilePath) {
        String blockFileName = blockFilePath.getFileName().toString();
        if (blockFileName.endsWith(BlockStreamFileWriterV1.COMPRESSION_ALGORITHM_EXTENSION)) {
            blockFileName = blockFileName.substring(
                    0, blockFileName.length() - BlockStreamFileWriterV1.COMPRESSION_ALGORITHM_EXTENSION.length());
        }
        return blockFilePath.resolveSibling(blockFileName + BLOCK_SIG_EXTENSION_SUFFIX);
    }
}
