package org.devzendo.zarjaz.protocol;

import java.lang.reflect.Method;

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
public class SampleInterfaces {
    public static Class<?> parameterType(Class<?> interfaceClass) {
        final Method method = interfaceClass.getDeclaredMethods()[0];
        return method.getParameterTypes()[0];
    }

    public interface BytePrimitiveInterface {
        void method(byte x);
    }

    public interface ByteWrapperInterface {
        void method(Byte x);
    }

    public interface IntPrimitiveInterface {
        void method(int x);
    }

    public interface IntWrapperInterface {
        void method(Integer x);
    }

    public interface LongPrimitiveInterface {
        void method(long x);
    }

    public interface LongWrapperInterface {
        void method(Long x);
    }

    public interface StringInterface {
        void method(String x);
    }

    public interface BooleanPrimitiveInterface {
        void method(boolean x);
    }

    public interface BooleanWrapperInterface {
        void method(Boolean x);
    }
}
