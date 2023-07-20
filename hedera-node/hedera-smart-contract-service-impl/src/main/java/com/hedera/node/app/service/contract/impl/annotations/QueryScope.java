package com.hedera.node.app.service.contract.impl.annotations;

import javax.inject.Scope;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Scope for bindings whose lifetime consists of a single {@code ContractCallLocal} query. (There are enough
 * of these that it is helpful to have some DI support.)
 */
@Target({METHOD, PARAMETER, TYPE})
@Retention(RUNTIME)
@Documented
@Scope
public @interface QueryScope {
}
