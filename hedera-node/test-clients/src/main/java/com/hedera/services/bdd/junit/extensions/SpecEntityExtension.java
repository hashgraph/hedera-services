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

package com.hedera.services.bdd.junit.extensions;

import static java.lang.reflect.Modifier.isStatic;
import static org.junit.platform.commons.support.AnnotationSupport.findAnnotatedFields;

import com.hedera.services.bdd.spec.dsl.SpecEntity;
import com.hedera.services.bdd.spec.dsl.annotations.AccountSpec;
import com.hedera.services.bdd.spec.dsl.annotations.ContractSpec;
import com.hedera.services.bdd.spec.dsl.annotations.FungibleTokenSpec;
import com.hedera.services.bdd.spec.dsl.annotations.KeySpec;
import com.hedera.services.bdd.spec.dsl.annotations.NonFungibleTokenSpec;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.dsl.entities.SpecFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecKey;
import com.hedera.services.bdd.spec.dsl.entities.SpecNonFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class SpecEntityExtension implements ParameterResolver, BeforeAllCallback {
    @Override
    public boolean supportsParameter(
            @NonNull final ParameterContext parameterContext, @NonNull final ExtensionContext extensionContext) {
        return SpecEntity.class.isAssignableFrom(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(
            @NonNull final ParameterContext parameterContext, @NonNull final ExtensionContext extensionContext)
            throws ParameterResolutionException {
        final var parameter = parameterContext.getParameter();
        final var entityType = parameterContext.getParameter().getType();
        if (entityType == SpecAccount.class) {
            return accountFrom(
                    parameter.isAnnotationPresent(AccountSpec.class)
                            ? parameter.getAnnotation(AccountSpec.class)
                            : null,
                    parameter.getName());
        } else if (entityType == SpecContract.class) {
            if (!parameter.isAnnotationPresent(ContractSpec.class)) {
                throw new IllegalArgumentException("Missing @ContractSpec annotation");
            }
            return contractFrom(parameter.getAnnotation(ContractSpec.class));
        } else if (entityType == SpecFungibleToken.class) {
            if (!parameter.isAnnotationPresent(FungibleTokenSpec.class)) {
                throw new IllegalArgumentException("Missing @FungibleTokenSpec annotation");
            }
            return fungibleTokenFrom(parameter.getAnnotation(FungibleTokenSpec.class), parameter.getName());
        } else if (entityType == SpecNonFungibleToken.class) {
            if (!parameter.isAnnotationPresent(NonFungibleTokenSpec.class)) {
                throw new IllegalArgumentException("Missing @NonFungibleTokenSpec annotation");
            }
            return nonFungibleTokenFrom(parameter.getAnnotation(NonFungibleTokenSpec.class), parameter.getName());
        } else if (entityType == SpecKey.class) {
            return keyFrom(
                    parameter.isAnnotationPresent(KeySpec.class) ? parameter.getAnnotation(KeySpec.class) : null,
                    parameter.getName());
        } else {
            throw new ParameterResolutionException("Unsupported entity type " + entityType);
        }
    }

    @Override
    public void beforeAll(@NonNull final ExtensionContext context) throws Exception {
        // Inject spec contracts into static fields annotated with @ContractSpec
        for (final var field : findAnnotatedFields(
                context.getRequiredTestClass(), ContractSpec.class, staticFieldSelector(SpecContract.class))) {
            final var contract = contractFrom(field.getAnnotation(ContractSpec.class));
            injectValueIntoField(field, contract);
        }

        // Inject spec fungible tokens into static fields annotated with @FungibleTokenSpec
        for (final var field : findAnnotatedFields(
                context.getRequiredTestClass(),
                FungibleTokenSpec.class,
                staticFieldSelector(SpecFungibleToken.class))) {
            final var token = fungibleTokenFrom(field.getAnnotation(FungibleTokenSpec.class), field.getName());
            injectValueIntoField(field, token);
        }

        // Inject spec non-fungible tokens into static fields annotated with @NonFungibleTokenSpec
        for (final var field : findAnnotatedFields(
                context.getRequiredTestClass(),
                NonFungibleTokenSpec.class,
                staticFieldSelector(SpecNonFungibleToken.class))) {
            final var token = nonFungibleTokenFrom(field.getAnnotation(NonFungibleTokenSpec.class), field.getName());
            injectValueIntoField(field, token);
        }

        // Inject spec accounts into static fields annotated with @AccountSpec
        for (final var field : findAnnotatedFields(
                context.getRequiredTestClass(), AccountSpec.class, staticFieldSelector(SpecAccount.class))) {
            final var account = accountFrom(field.getAnnotation(AccountSpec.class), field.getName());
            injectValueIntoField(field, account);
        }

        // Inject spec keys into static fields annotated with @KeySpec
        for (final var field : findAnnotatedFields(
                context.getRequiredTestClass(), KeySpec.class, staticFieldSelector(SpecKey.class))) {
            final var key = keyFrom(field.getAnnotation(KeySpec.class), field.getName());
            injectValueIntoField(field, key);
        }
    }

    private SpecContract contractFrom(@NonNull final ContractSpec annotation) {
        final var name = annotation.name().isBlank() ? annotation.contract() : annotation.name();
        return new SpecContract(name, annotation.contract(), annotation.creationGas());
    }

    private SpecNonFungibleToken nonFungibleTokenFrom(
            @NonNull final NonFungibleTokenSpec annotation, @NonNull final String defaultName) {
        final var token = new SpecNonFungibleToken(annotation.name().isBlank() ? defaultName : annotation.name());
        customizeToken(token, annotation.keys(), annotation.useAutoRenewAccount());
        token.setNumPreMints(annotation.numPreMints());
        return token;
    }

    private SpecFungibleToken fungibleTokenFrom(
            @NonNull final FungibleTokenSpec annotation, @NonNull final String defaultName) {
        final var token = new SpecFungibleToken(annotation.name().isBlank() ? defaultName : annotation.name());
        customizeToken(token, annotation.keys(), annotation.useAutoRenewAccount());
        return token;
    }

    private SpecAccount accountFrom(@Nullable final AccountSpec annotation, @NonNull final String defaultName) {
        final var name = Optional.ofNullable(annotation)
                .map(AccountSpec::name)
                .filter(n -> !n.isBlank())
                .orElse(defaultName);
        return new SpecAccount(name);
    }

    private SpecKey keyFrom(@Nullable final KeySpec annotation, @NonNull final String defaultName) {
        final var name = Optional.ofNullable(annotation)
                .map(KeySpec::name)
                .filter(n -> !n.isBlank())
                .orElse(defaultName);
        final var type = Optional.ofNullable(annotation).map(KeySpec::type).orElse(SpecKey.Type.ED25519);
        return new SpecKey(name, type);
    }

    private void customizeToken(
            @NonNull final SpecToken token, @NonNull final SpecTokenKey[] keys, final boolean useAutoRenewAccount) {
        token.setKeys(EnumSet.copyOf(List.of(keys)));
        if (useAutoRenewAccount) {
            token.useAutoRenewAccount();
        }
    }

    private void injectValueIntoField(@NonNull final Field field, @NonNull final Object value)
            throws IllegalAccessException {
        final var accessible = field.isAccessible();
        field.setAccessible(true);
        field.set(null, value);
        field.setAccessible(accessible);
    }

    private static Predicate<Field> staticFieldSelector(@NonNull final Class<?> type) {
        return f -> isStatic(f.getModifiers()) && f.getType() == type;
    }
}
