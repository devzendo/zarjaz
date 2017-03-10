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
public class DefaultInvocationHashGenerator implements InvocationHashGenerator {
    private final MessageDigest md5;
    private final Charset utf8Charset;

    public DefaultInvocationHashGenerator() {
        try {
            this.md5 = MessageDigest.getInstance("MD5");
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot construct MD5 MessageDigest");
        }

        this.utf8Charset = Charset.forName("UTF-8");
    }

    @Override
    public Map<Method, byte[]> generate(final EndpointName endpointName, final Class<?> interfaceClass) {
        if (endpointName == null) {
            throw new IllegalArgumentException("Cannot generate hashes for endpoint name 'null'");
        }
        if (interfaceClass == null) {
            throw new IllegalArgumentException("Cannot generate hashes for 'null'");
        }
        final byte[] endpointNameUTF8 = toUTF8(endpointName.toString());
        final Map<Method, byte[]> map = new HashMap<>();
        for (Method method: interfaceClass.getMethods()) {
            final byte[] hash = generateHash(endpointNameUTF8, method);
            map.put(method, hash);
        }
        return map;
    }

    private byte[] generateHash(final byte[] endpointNameUTF8, final Method method) {
        md5.reset();
        md5.update(endpointNameUTF8);
        // TODO add endpointName.getVersion() when we have that.

        md5.update(toUTF8(method.getReturnType().getSimpleName())); // wonder whether return type needed, java doesn't distinguish methods by it?
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
