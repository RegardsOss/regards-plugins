/*
 * Copyright 2017-2021 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

import fr.cnes.regards.modules.catalog.stac.domain.error.StacException;
import fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType;
import io.vavr.CheckedConsumer;
import io.vavr.CheckedFunction0;
import io.vavr.CheckedFunction1;
import io.vavr.control.Try;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Provides a small DSL to build {@link Try} instances, forcing the user to
 * declare a "onFailure".
 */
public final class TryDSL {

    public interface TryFailureBuilder<R> {
        CheckedFunction0<R> trying();

        default <T> TryFailureBuilder<T> map(CheckedFunction1<R, T> mapFn) {
            return () -> trying().andThen(mapFn);
        }

        default Try<R> onFailure(CheckedConsumer<Throwable> recover) {
            return Try.of(trying())
                .onFailure(t -> Try.run(() -> recover.accept(t)));
        }

        default Try<R> mapFailure(Function<Throwable, StacException> cb) {
            return Try.of(trying())
                .recoverWith(t -> Try.failure(cb.apply(t)));
        }

        default Try<R> mapFailure(StacFailureType type, Supplier<String> message) {
            return mapFailure(t -> new StacException(message.get(), t, type));
        }

    }

    public static <R> TryFailureBuilder<R> trying(CheckedFunction0<R> fn) {
        return () -> fn;
    }

    public static <R> Try<R> tryingManaged(CheckedFunction0<R> fn) {
        return Try.of(fn);
    }

    /**
     * To be used in recoverWith.
     * @param type
     * @param message
     * @param <R>
     * @return
     */
    public static <R> Function<? super Throwable, ? extends Try<? extends R>> stacFailure(
            StacFailureType type,
            Supplier<String> message
    ){
        return t -> Try.failure(new StacException(message.get(), t, type));
    }

}
