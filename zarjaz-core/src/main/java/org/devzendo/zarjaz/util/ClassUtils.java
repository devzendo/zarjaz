package org.devzendo.zarjaz.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Copyright (C) 2008-2016 Matt Gumbley, DevZendo.org http://devzendo.org
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class ClassUtils {
    static public Class[] objectsToClasses(final Object[] args) {
        if (args == null) {
            return null;
        }
        final Class[] out = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            final Class<?> klas = args[i].getClass();
            out[i] = klas.isPrimitive() ? primitiveTypeOf(klas) : klas;
        }
        return out;
    }

    static private final Map<Class, Class> primitiveLookup = new HashMap<>();

    static {
        primitiveLookup.put(Boolean.class, Boolean.TYPE);
        primitiveLookup.put(Byte.class, Byte.TYPE);
        primitiveLookup.put(Character.class, Character.TYPE);
        primitiveLookup.put(Short.class, Short.TYPE);
        primitiveLookup.put(Integer.class, Integer.TYPE);
        primitiveLookup.put(Long.class, Long.TYPE);
        primitiveLookup.put(Double.class, Double.TYPE);
        primitiveLookup.put(Float.class, Float.TYPE);
        primitiveLookup.put(Void.class, Void.TYPE);
    }

    static public Class primitiveTypeOf(final Class actuallyPrimitive) {
        final Class primitiveType = primitiveLookup.get(actuallyPrimitive);
        if (primitiveType == null) {
            throw new IllegalArgumentException("Cannot convert " +
                    (actuallyPrimitive == null ? actuallyPrimitive : actuallyPrimitive.getSimpleName()) +
                    " to a primitive type");
        }
        return primitiveType;
    }

    static public String joinedClassNames(final Class[] argClasses) {
        final StringBuilder sb = new StringBuilder();
        sb.append('(');
        if (argClasses != null) {
            for (int i = 0; i < argClasses.length; i++) {
                sb.append(argClasses[i].getSimpleName());
                if (i != (argClasses.length - 1)) {
                    sb.append(", ");
                }
            }

        }
        sb.append(')');
        return sb.toString();
    }

    static public String[] classesToClassNames(final Class[] argClasses) {
        if (argClasses == null) {
            return null;
        }
        final String[] out = new String[argClasses.length];
        for (int i = 0; i < argClasses.length; i++) {
            out[i] = argClasses[i].getSimpleName();
        }
        return out;
    }
}
