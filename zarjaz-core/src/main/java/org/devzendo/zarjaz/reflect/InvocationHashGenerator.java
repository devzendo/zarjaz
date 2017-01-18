package org.devzendo.zarjaz.reflect;

import org.devzendo.zarjaz.transport.EndpointName;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
public class InvocationHashGenerator {
    private final EndpointName endpointName;
    private final MessageDigest md5;
    private final Charset utf8Charset;
    private final byte[] endpointNameUTF8;

    public InvocationHashGenerator(final EndpointName endpointName) {
        if (endpointName == null) {
            throw new IllegalArgumentException("Cannot generate hashes for endpoint name 'null'");
        }
        this.endpointName = endpointName;

        try {
            this.md5 = MessageDigest.getInstance("MD5");
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot construct MD5 MessageDigest");
        }

        this.utf8Charset = Charset.forName("UTF-8");
        this.endpointNameUTF8 = toUTF8(endpointName.toString());
    }

    public Map<Method, byte[]> generate(final Class<?> interfaceClass) {
        if (interfaceClass == null) {
            throw new IllegalArgumentException("Cannot generate hashes for 'null'");
        }
        final Map<Method, byte[]> map = new HashMap<>();
        for (Method method: interfaceClass.getMethods()) {
            final byte[] hash = generateHash(endpointName, method);
            map.put(method, hash);
        }
        return map;
    }

    private byte[] generateHash(final EndpointName endpointName, final Method method) {
        md5.reset();
        md5.update(endpointNameUTF8);
        // TODO add endpointName.getVersion() when we have that.

        md5.update(toUTF8(method.getReturnType().getSimpleName()));
        md5.update(toUTF8(method.getName()));
        // this is always an array, never null
        for (final Class paramType: method.getParameterTypes()) {
            md5.update(toUTF8(paramType.getSimpleName()));
        }
        return md5.digest();
    }

    private byte[] toUTF8(final String string) {
        return string.getBytes(utf8Charset);
    }
}
