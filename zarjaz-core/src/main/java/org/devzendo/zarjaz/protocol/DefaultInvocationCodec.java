package org.devzendo.zarjaz.protocol;

import org.devzendo.zarjaz.transport.EndpointName;
import org.devzendo.zarjaz.transport.NamedInterface;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.*;

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
public class DefaultInvocationCodec implements InvocationCodec {
    private Object lock = new Object();
    private final Map<byte[], EndpointInterfaceMethod> hashToMethod = new HashMap<>();
    private final Map<NamedInterface<?>, Map<Method, byte[]>> namedInterfaceMethodMap = new HashMap<>();

    @Override
    public Optional<EndpointInterfaceMethod> registerHashes(final EndpointName endpointName, final Class<?> interfaceClass, final Map<Method, byte[]> methodMap) {
        synchronized (lock) {
            // First detect any potential collisions... for now, return the first.
            for (byte[] hashToAdd: methodMap.values()) {
                final EndpointInterfaceMethod existingEndpointInterfaceMethod = hashToMethod.get(hashToAdd);
                if (existingEndpointInterfaceMethod != null) {
                    return Optional.of(existingEndpointInterfaceMethod);
                }
            }

            // No collisions, register the incoming hashes
            final Map<Method, byte[]> hashMap = new HashMap<>();
            methodMap.forEach((Method method, byte[] hash) -> {
                hashToMethod.put(hash, new EndpointInterfaceMethod(endpointName, interfaceClass, method));
                hashMap.put(method, hash);
            });
            namedInterfaceMethodMap.put(new NamedInterface<>(endpointName, interfaceClass), hashMap);
        }
        return Optional.empty();
    }

    @Override
    // TODO this is really just internal now - tell, don't ask.
    public Map<Method, byte[]> getMethodsToHashMap(final EndpointName endpointName, final Class<?> interfaceClass) {
        synchronized (lock) {
            return Collections.unmodifiableMap(namedInterfaceMethodMap.get(new NamedInterface<>(endpointName, interfaceClass)));
        }
    }

    @Override
    public List<ByteBuffer> generateHashedMethodInvocation(int sequence, final EndpointName endpointName, final Class<?> interfaceClass, final Method method, final Object[] args) {
        final byte[] hash = getHash(endpointName, interfaceClass, method);
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeByte(Protocol.InitialFrameType.METHOD_INVOCATION_HASHED.getInitialFrameType());
        encoder.writeInt(sequence);
        encoder.writeBytes(hash);
        final Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            encoder.writeObject(parameterTypes[i], args[i]);
        }
        return encoder.getBuffers();
    }

    private byte[] getHash(final EndpointName endpointName, final Class<?> interfaceClass, final Method method) {
        synchronized (lock) {
            return namedInterfaceMethodMap.get(new NamedInterface<>(endpointName, interfaceClass)).get(method);
        }
    }
}
