/*
 * Copyright 2017-2024 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.catalog.stac.testutils.random;

import org.jeasy.random.EasyRandom;
import org.jeasy.random.ObjectCreationException;
import org.jeasy.random.api.ObjectFactory;
import org.jeasy.random.api.RandomizerContext;
import org.jeasy.random.util.ReflectionUtils;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Random;

/**
 * Workaround for EasyRandom not supporting records.
 */
public class RecordFactory implements ObjectFactory {

    private final Objenesis objenesis = new ObjenesisStd();

    private EasyRandom easyRandom;

    @Override
    public <T> T createInstance(Class<T> type, RandomizerContext randomizerContext) throws ObjectCreationException {
        if (easyRandom == null) {
            easyRandom = new EasyRandom(randomizerContext.getParameters());
        }

        if (type.isRecord()) {
            return createRandomRecord(type);
        } else {
            return createRandomInstance(type, randomizerContext);
        }
    }

    private <T> T createRandomRecord(Class<T> recordType) {
        // generate random values for record components
        RecordComponent[] recordComponents = recordType.getRecordComponents();
        Object[] randomValues = new Object[recordComponents.length];
        for (int i = 0; i < recordComponents.length; i++) {
            randomValues[i] = easyRandom.nextObject(recordComponents[i].getType());
        }
        // create a random instance with random values
        try {
            return getCanonicalConstructor(recordType).newInstance(randomValues);
        } catch (Exception e) {
            throw new ObjectCreationException("Unable to create a random instance of recordType " + recordType, e);
        }
    }

    private <T> Constructor<T> getCanonicalConstructor(Class<T> recordType) {
        RecordComponent[] recordComponents = recordType.getRecordComponents();
        Class<?>[] componentTypes = new Class<?>[recordComponents.length];
        for (int i = 0; i < recordComponents.length; i++) {
            // recordComponents are ordered, see javadoc:
            // "The components are returned in the same order that they are declared in the record header"
            componentTypes[i] = recordComponents[i].getType();
        }
        try {
            return recordType.getDeclaredConstructor(componentTypes);
        } catch (NoSuchMethodException e) {
            // should not happen, from Record javadoc:
            // "A record class has the following mandated members: a public canonical constructor ,
            // whose descriptor is the same as the record descriptor;"
            throw new RuntimeException("Invalid record definition", e);
        }
    }

    private <T> T createRandomInstance(Class<T> type, RandomizerContext context) {
        if (context.getParameters().isScanClasspathForConcreteTypes() && ReflectionUtils.isAbstract(type)) {
            Class<?> randomConcreteSubType = CollectionUtils.randomElementOf(ReflectionUtils.getPublicConcreteSubTypesOf(
                type));
            if (randomConcreteSubType == null) {
                throw new InstantiationError("Unable to find a matching concrete subtype of type: "
                                             + type
                                             + " in the classpath");
            } else {
                return (T) this.createNewInstance(randomConcreteSubType);
            }
        } else {
            try {
                return this.createNewInstance(type);
            } catch (Error var4) {
                Error e = var4;
                throw new ObjectCreationException("Unable to create an instance of type: " + type, e);
            }
        }
    }

    private <T> T createNewInstance(Class<T> type) {
        try {
            Constructor<T> noArgConstructor = type.getDeclaredConstructor();
            if (!noArgConstructor.isAccessible()) {
                noArgConstructor.setAccessible(true);
            }

            return noArgConstructor.newInstance();
        } catch (Exception var3) {
            return this.objenesis.newInstance(type);
        }
    }

    private static final class CollectionUtils {

        private CollectionUtils() {
        }

        /**
         * Get a random element from the list.
         *
         * @param list the input list
         * @param <T>  the type of elements in the list
         * @return a random element from the list or null if the list is empty
         */
        public static <T> T randomElementOf(final List<T> list) {
            if (list.isEmpty()) {
                return null;
            }
            return list.get(nextInt(0, list.size()));
        }

        private static int nextInt(int startInclusive, int endExclusive) {
            return startInclusive + new Random().nextInt(endExclusive - startInclusive);
        }
    }
}
