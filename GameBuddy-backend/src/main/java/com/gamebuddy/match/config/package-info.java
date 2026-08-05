/**
 * Service configuration.
 *
 * <p>{@code @NullMarked} so that nullness is specified rather than unknown: Spring's own
 * packages are marked, and an override in an unmarked package cannot state a contract
 * compatible with the interface it implements (java:S2638). Within this package every
 * type is non-null unless annotated {@code @Nullable}.
 */
@NullMarked
package com.gamebuddy.match.config;

import org.jspecify.annotations.NullMarked;
