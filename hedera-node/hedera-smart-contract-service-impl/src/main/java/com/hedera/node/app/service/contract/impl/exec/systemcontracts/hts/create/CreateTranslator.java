// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create;

import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.ARRAY_BRACKETS;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FIXED_FEE;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FIXED_FEE_V2;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FRACTIONAL_FEE;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FRACTIONAL_FEE_V2;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.HEDERA_TOKEN_V1;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.HEDERA_TOKEN_V2;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.HEDERA_TOKEN_V3;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.HEDERA_TOKEN_WITH_METADATA;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.ROYALTY_FEE;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.ROYALTY_FEE_V2;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.metrics.ContractMetrics;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.utils.SystemContractMethod;
import com.hedera.node.app.service.contract.impl.exec.utils.SystemContractMethod.Category;
import com.hedera.node.app.service.contract.impl.exec.utils.SystemContractMethod.Variant;
import com.hedera.node.app.service.contract.impl.exec.utils.SystemContractMethodRegistry;
import com.hedera.node.config.data.ContractsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Translates {@code createFungibleToken}, {@code createNonFungibleToken},
 * {@code createFungibleTokenWithCustomFees} and {@code createNonFungibleTokenWithCustomFees} calls to the HTS system contract.
 */
@Singleton
public class CreateTranslator extends AbstractCallTranslator<HtsCallAttempt> {

    /** Selector for createFungibleToken(HEDERA_TOKEN_V1,uint,uint) method. */
    public static final SystemContractMethod CREATE_FUNGIBLE_TOKEN_V1 = SystemContractMethod.declare(
                    "createFungibleToken(" + HEDERA_TOKEN_V1 + ",uint,uint)", "(int64,address)")
            .withVariants(Variant.V1, Variant.FT)
            .withCategory(Category.CREATE_DELETE_TOKEN);
    /** Selector for createFungibleToken(HEDERA_TOKEN_V2,uint64,uint32) method. */
    public static final SystemContractMethod CREATE_FUNGIBLE_TOKEN_V2 = SystemContractMethod.declare(
                    "createFungibleToken(" + HEDERA_TOKEN_V2 + ",uint64,uint32)", "(int64,address)")
            .withVariants(Variant.V2, Variant.FT)
            .withCategory(Category.CREATE_DELETE_TOKEN);
    /** Selector for createFungibleToken(HEDERA_TOKEN_V3,int64,int32) method. */
    public static final SystemContractMethod CREATE_FUNGIBLE_TOKEN_V3 = SystemContractMethod.declare(
                    "createFungibleToken(" + HEDERA_TOKEN_V3 + ",int64,int32)", "(int64,address)")
            .withVariants(Variant.V3, Variant.FT)
            .withCategory(Category.CREATE_DELETE_TOKEN);
    /** Selector for createFungibleTokenWithCustomFees(HEDERA_TOKEN_V1,uint,uint,FIXED_FEE[],FRACTIONAL_FEE[]) method. */
    public static final SystemContractMethod CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1 = SystemContractMethod.declare(
                    "createFungibleTokenWithCustomFees("
                            + HEDERA_TOKEN_V1
                            + ",uint,uint,"
                            + FIXED_FEE
                            + ARRAY_BRACKETS
                            + ","
                            + FRACTIONAL_FEE
                            + ARRAY_BRACKETS
                            + ")",
                    "(int64,address)")
            .withVariants(Variant.V1, Variant.FT, Variant.WITH_CUSTOM_FEES)
            .withCategory(Category.CREATE_DELETE_TOKEN);
    /** Selector for createFungibleTokenWithCustomFees(HEDERA_TOKEN_V2,uint64,uint32,FIXED_FEE[],FRACTIONAL_FEE[]) method. */
    public static final SystemContractMethod CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V2 = SystemContractMethod.declare(
                    "createFungibleTokenWithCustomFees("
                            + HEDERA_TOKEN_V2
                            + ",uint64,uint32,"
                            + FIXED_FEE
                            + ARRAY_BRACKETS
                            + ","
                            + FRACTIONAL_FEE
                            + ARRAY_BRACKETS
                            + ")",
                    "(int64,address)")
            .withVariants(Variant.V2, Variant.FT, Variant.WITH_CUSTOM_FEES)
            .withCategory(Category.CREATE_DELETE_TOKEN);
    /** Selector for createFungibleTokenWithCustomFees(HEDERA_TOKEN_V3,int64,int32,FIXED_FEE_V2[],FRACTIONAL_FEE_V2[]) method. */
    public static final SystemContractMethod CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V3 = SystemContractMethod.declare(
                    "createFungibleTokenWithCustomFees("
                            + HEDERA_TOKEN_V3
                            + ",int64,int32,"
                            + FIXED_FEE_V2
                            + ARRAY_BRACKETS
                            + ","
                            + FRACTIONAL_FEE_V2
                            + ARRAY_BRACKETS
                            + ")",
                    "(int64,address)")
            .withVariants(Variant.V3, Variant.FT, Variant.WITH_CUSTOM_FEES)
            .withCategory(Category.CREATE_DELETE_TOKEN);

    /** Selector for createNonFungibleToken(HEDERA_TOKEN_V1) method. */
    public static final SystemContractMethod CREATE_NON_FUNGIBLE_TOKEN_V1 = SystemContractMethod.declare(
                    "createNonFungibleToken(" + HEDERA_TOKEN_V1 + ")", "(int64,address)")
            .withVariants(Variant.V1, Variant.NFT)
            .withCategory(Category.CREATE_DELETE_TOKEN);
    /** Selector for createNonFungibleToken(HEDERA_TOKEN_V2) method. */
    public static final SystemContractMethod CREATE_NON_FUNGIBLE_TOKEN_V2 = SystemContractMethod.declare(
                    "createNonFungibleToken(" + HEDERA_TOKEN_V2 + ")", "(int64,address)")
            .withVariants(Variant.V2, Variant.NFT)
            .withCategory(Category.CREATE_DELETE_TOKEN);
    /** Selector for createNonFungibleToken(HEDERA_TOKEN_V3) method. */
    public static final SystemContractMethod CREATE_NON_FUNGIBLE_TOKEN_V3 = SystemContractMethod.declare(
                    "createNonFungibleToken(" + HEDERA_TOKEN_V3 + ")", "(int64,address)")
            .withVariants(Variant.V3, Variant.NFT)
            .withCategory(Category.CREATE_DELETE_TOKEN);

    /** Selector for createNonFungibleTokenWithCustomFees(HEDERA_TOKEN_V1,int64,int32,FIXED_FEE[],FRACTIONAL_FEE[]) method. */
    public static final SystemContractMethod CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1 =
            SystemContractMethod.declare(
                            "createNonFungibleTokenWithCustomFees("
                                    + HEDERA_TOKEN_V1
                                    + ","
                                    + FIXED_FEE
                                    + ARRAY_BRACKETS
                                    + ","
                                    + ROYALTY_FEE
                                    + ARRAY_BRACKETS
                                    + ")",
                            "(int64,address)")
                    .withVariants(Variant.V1, Variant.NFT, Variant.WITH_CUSTOM_FEES)
                    .withCategory(Category.CREATE_DELETE_TOKEN);
    /** Selector for createNonFungibleTokenWithCustomFees(HEDERA_TOKEN_V2,int64,int32,FIXED_FEE[],FRACTIONAL_FEE[]) method. */
    public static final SystemContractMethod CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2 =
            SystemContractMethod.declare(
                            "createNonFungibleTokenWithCustomFees("
                                    + HEDERA_TOKEN_V2
                                    + ","
                                    + FIXED_FEE
                                    + ARRAY_BRACKETS
                                    + ","
                                    + ROYALTY_FEE
                                    + ARRAY_BRACKETS
                                    + ")",
                            "(int64,address)")
                    .withVariants(Variant.V2, Variant.NFT, Variant.WITH_CUSTOM_FEES)
                    .withCategory(Category.CREATE_DELETE_TOKEN);
    /** Selector for createNonFungibleTokenWithCustomFees(HEDERA_TOKEN_V3,int64,int32,FIXED_FEE_2[],FRACTIONAL_FEE_2[]) method. */
    public static final SystemContractMethod CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3 =
            SystemContractMethod.declare(
                            "createNonFungibleTokenWithCustomFees("
                                    + HEDERA_TOKEN_V3
                                    + ","
                                    + FIXED_FEE_V2
                                    + ARRAY_BRACKETS
                                    + ","
                                    + ROYALTY_FEE_V2
                                    + ARRAY_BRACKETS
                                    + ")",
                            "(int64,address)")
                    .withVariants(Variant.V3, Variant.NFT, Variant.WITH_CUSTOM_FEES)
                    .withCategory(Category.CREATE_DELETE_TOKEN);
    /** Selector for createFungibleTokenWithCustomFees(HEDERA_TOKEN_WITH_METADATA,int64,int32) method. */
    public static final SystemContractMethod CREATE_FUNGIBLE_TOKEN_WITH_METADATA = SystemContractMethod.declare(
                    "createFungibleToken(" + HEDERA_TOKEN_WITH_METADATA + ",int64,int32)", "(int64,address)")
            .withVariants(Variant.FT, Variant.WITH_METADATA)
            .withCategory(Category.CREATE_DELETE_TOKEN);
    /** Selector for createFungibleTokenWithCustomFees(HEDERA_TOKEN_WITH_METADATA,int64,int32,FIXED_FEE_2[],FRACTIONAL_FEE_2[]) method. */
    public static final SystemContractMethod CREATE_FUNGIBLE_TOKEN_WITH_METADATA_AND_CUSTOM_FEES =
            SystemContractMethod.declare(
                            "createFungibleTokenWithCustomFees("
                                    + HEDERA_TOKEN_WITH_METADATA
                                    + ",int64,int32,"
                                    + FIXED_FEE_V2
                                    + ARRAY_BRACKETS
                                    + ","
                                    + FRACTIONAL_FEE_V2
                                    + ARRAY_BRACKETS
                                    + ")",
                            "(int64,address)")
                    .withVariants(Variant.FT, Variant.WITH_METADATA, Variant.WITH_CUSTOM_FEES)
                    .withCategory(Category.CREATE_DELETE_TOKEN);
    /** Selector for createNonFungibleToken(HEDERA_TOKEN_WITH_METADATA) method. */
    public static final SystemContractMethod CREATE_NON_FUNGIBLE_TOKEN_WITH_METADATA = SystemContractMethod.declare(
                    "createNonFungibleToken(" + HEDERA_TOKEN_WITH_METADATA + ")", "(int64,address)")
            .withVariants(Variant.NFT, Variant.WITH_METADATA)
            .withCategory(Category.CREATE_DELETE_TOKEN);
    /** Selector for createNonFungibleTokenWithCustomFees(HEDERA_TOKEN_WITH_METADATA,FIXED_FEE_2[],FRACTIONAL_FEE_2[]) method. */
    public static final SystemContractMethod CREATE_NON_FUNGIBLE_TOKEN_WITH_METADATA_AND_CUSTOM_FEES =
            SystemContractMethod.declare(
                            "createNonFungibleTokenWithCustomFees("
                                    + HEDERA_TOKEN_WITH_METADATA
                                    + ","
                                    + FIXED_FEE_V2
                                    + ARRAY_BRACKETS
                                    + ","
                                    + ROYALTY_FEE_V2
                                    + ARRAY_BRACKETS
                                    + ")",
                            "(int64,address)")
                    .withVariants(Variant.NFT, Variant.WITH_METADATA, Variant.WITH_CUSTOM_FEES)
                    .withCategory(Category.CREATE_DELETE_TOKEN);

    /**
     * A set of `Function` objects representing various create functions for fungible and non-fungible tokens.
     * This set is used in {@link com.hedera.node.app.service.contract.impl.exec.processors.CustomMessageCallProcessor}
     * to determine if a given call attempt is a creation call, because we do not allow sending value to Hedera system contracts
     * except in the case of token creation
     */
    public static final Map<SystemContractMethod, CreateDecoderFunction> createMethodsMap = new HashMap<>();

    /**
     * Constructor for injection.
     * @param decoder the decoder used to decode create calls
     */
    @Inject
    public CreateTranslator(
            final CreateDecoder decoder,
            @NonNull final SystemContractMethodRegistry systemContractMethodRegistry,
            @NonNull final ContractMetrics contractMetrics) {
        super(SystemContractMethod.SystemContract.HTS, systemContractMethodRegistry, contractMetrics);

        registerMethods(
                CREATE_FUNGIBLE_TOKEN_V1,
                CREATE_FUNGIBLE_TOKEN_V2,
                CREATE_FUNGIBLE_TOKEN_V3,
                CREATE_FUNGIBLE_TOKEN_WITH_METADATA,
                CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1,
                CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V2,
                CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V3,
                CREATE_FUNGIBLE_TOKEN_WITH_METADATA_AND_CUSTOM_FEES,
                CREATE_NON_FUNGIBLE_TOKEN_V1,
                CREATE_NON_FUNGIBLE_TOKEN_V2,
                CREATE_NON_FUNGIBLE_TOKEN_V3,
                CREATE_NON_FUNGIBLE_TOKEN_WITH_METADATA,
                CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1,
                CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2,
                CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3,
                CREATE_NON_FUNGIBLE_TOKEN_WITH_METADATA_AND_CUSTOM_FEES);

        createMethodsMap.put(CREATE_FUNGIBLE_TOKEN_V1, decoder::decodeCreateFungibleTokenV1);
        createMethodsMap.put(CREATE_FUNGIBLE_TOKEN_V2, decoder::decodeCreateFungibleTokenV2);
        createMethodsMap.put(CREATE_FUNGIBLE_TOKEN_V3, decoder::decodeCreateFungibleTokenV3);
        createMethodsMap.put(CREATE_FUNGIBLE_TOKEN_WITH_METADATA, decoder::decodeCreateFungibleTokenWithMetadata);
        createMethodsMap.put(CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1, decoder::decodeCreateFungibleTokenWithCustomFeesV1);
        createMethodsMap.put(CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V2, decoder::decodeCreateFungibleTokenWithCustomFeesV2);
        createMethodsMap.put(CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V3, decoder::decodeCreateFungibleTokenWithCustomFeesV3);
        createMethodsMap.put(
                CREATE_FUNGIBLE_TOKEN_WITH_METADATA_AND_CUSTOM_FEES,
                decoder::decodeCreateFungibleTokenWithMetadataAndCustomFees);
        createMethodsMap.put(CREATE_NON_FUNGIBLE_TOKEN_V1, decoder::decodeCreateNonFungibleV1);
        createMethodsMap.put(CREATE_NON_FUNGIBLE_TOKEN_V2, decoder::decodeCreateNonFungibleV2);
        createMethodsMap.put(CREATE_NON_FUNGIBLE_TOKEN_V3, decoder::decodeCreateNonFungibleV3);
        createMethodsMap.put(CREATE_NON_FUNGIBLE_TOKEN_WITH_METADATA, decoder::decodeCreateNonFungibleWithMetadata);
        createMethodsMap.put(
                CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1, decoder::decodeCreateNonFungibleWithCustomFeesV1);
        createMethodsMap.put(
                CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2, decoder::decodeCreateNonFungibleWithCustomFeesV2);
        createMethodsMap.put(
                CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3, decoder::decodeCreateNonFungibleWithCustomFeesV3);
        createMethodsMap.put(
                CREATE_NON_FUNGIBLE_TOKEN_WITH_METADATA_AND_CUSTOM_FEES,
                decoder::decodeCreateNonFungibleWithMetadataAndCustomFees);
    }

    @Override
    public @NonNull Optional<SystemContractMethod> identifyMethod(@NonNull HtsCallAttempt attempt) {
        requireNonNull(attempt);
        final var metaConfigEnabled =
                attempt.configuration().getConfigData(ContractsConfig.class).metadataKeyAndFieldEnabled();

        for (final var method : createMethodsMap.keySet()) {
            final var isMetadataMethod = method.hasVariant(Variant.WITH_METADATA);

            Optional<SystemContractMethod> m = Optional.empty();
            if (isMetadataMethod) {
                if (metaConfigEnabled) m = attempt.isMethod(method);
            } else {
                m = attempt.isMethod(method);
            }
            if (m.isPresent()) return m;
        }
        return Optional.empty();
    }

    @Override
    public ClassicCreatesCall callFrom(@NonNull HtsCallAttempt attempt) {
        return new ClassicCreatesCall(
                attempt.systemContractGasCalculator(),
                attempt.enhancement(),
                nominalBodyFor(attempt),
                attempt.defaultVerificationStrategy(),
                attempt.senderId());
    }

    private @Nullable TransactionBody nominalBodyFor(@NonNull final HtsCallAttempt attempt) {
        final var inputBytes = attempt.inputBytes();
        final var senderId = attempt.senderId();
        final var nativeOperations = attempt.nativeOperations();
        final var addressIdConverter = attempt.addressIdConverter();

        return createMethodsMap.entrySet().stream()
                .filter(entry -> attempt.isSelector(entry.getKey()))
                .map(entry -> entry.getValue().decode(inputBytes, senderId, nativeOperations, addressIdConverter))
                .findFirst()
                .orElse(null);
    }
}
