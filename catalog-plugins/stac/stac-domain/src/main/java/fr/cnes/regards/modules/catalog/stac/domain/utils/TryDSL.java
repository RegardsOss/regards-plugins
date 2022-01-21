/*
 * Copyright 2017-2022 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
 *
 * This file is part of REGARDS.
 *
 * REGARDS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * REGARDS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with REGARDS. If not, see <http://www.gnu.org/licenses/>.
 */

package fr.cnes.regards.modules.catalog.stac.domain.utils;

import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.cnes.regards.modules.catalog.stac.domain.error.StacException;
import fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType;
import io.vavr.CheckedConsumer;
import io.vavr.CheckedFunction0;
import io.vavr.CheckedFunction1;
import io.vavr.control.Try;

/**
 * Provides a small DSL to build {@link Try} instances, forcing the user to
 * declare an "onFailure".
 */
public interface TryDSL<R> {

    static final Logger LOGGER = LoggerFactory.getLogger(TryDSL.class);

    Try<R> trying();

    default <T> TryDSL<T> map(CheckedFunction1<R, T> mapFn) {
        return () -> trying().mapTry(mapFn);
    }

    default <T> TryDSL<T> flatMap(CheckedFunction1<R, Try<T>> fmapFn) {
        return () -> trying().flatMapTry(fmapFn);
    }

    default Try<R> onFailure(CheckedConsumer<Throwable> recover) {
        return trying().onFailure(t -> Try.run(() -> recover.accept(t)));
    }

    default Try<R> mapFailure(Function<Throwable, StacException> cb) {
        return trying().recoverWith(t -> {
            LOGGER.error("Failure", t);
            return Try.failure(cb.apply(t));
        });
    }

    default Try<R> mapFailure(StacFailureType type, Supplier<String> message) {
        return mapFailure(t -> new StacException(message.get(), t, type));
    }

    static <R> TryDSL<R> trying(CheckedFunction0<R> fn) {
        return () -> Try.of(fn);
    }

    /**
     * When you really need to use Try.of, use this method instead. This allows to track usage of
     * Try.of elsewhere in the code.
     *
     * This method should be used sparingly, only for very special cases when the
     * expected exceptions are of very little importance or when the Try can be
     * safely discarded into an Option.
     */
    static <R> Try<R> tryOf(CheckedFunction0<R> fn) {
        return Try.of(fn);
    }

    /**
     * To be used in recoverWith.
     */
    static <R> Function<? super Throwable, ? extends Try<? extends R>> stacFailure(StacFailureType type,
            Supplier<String> message) {
        return t -> Try.failure(new StacException(message.get(), t, type));
    }

}
