package org.devzendo.zarjaz.transport;

import org.devzendo.zarjaz.protocol.InvocationCodec;
import org.devzendo.zarjaz.reflect.InvocationHashGenerator;
import org.devzendo.zarjaz.timeout.TimeoutScheduler;
import org.devzendo.zarjaz.transceiver.Transceiver;
import org.devzendo.zarjaz.validation.ClientInterfaceValidator;
import org.devzendo.zarjaz.validation.ServerImplementationValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
public class TransceiverTransport extends AbstractTransport implements Transport {
    private static final Logger logger = LoggerFactory.getLogger(TransceiverTransport.class);
    private final Transceiver transceiver;
    private final InvocationHashGenerator invocationHashGenerator;
    private final InvocationCodec invocationCodec;
    private final AtomicInteger sequence = new AtomicInteger(0);
    private final Map<Integer, CompletableFuture<Object>> outstandingMethodCalls = new ConcurrentHashMap<>();

    public TransceiverTransport(final ServerImplementationValidator serverImplementationValidator, final ClientInterfaceValidator clientInterfaceValidator, final TimeoutScheduler timeoutScheduler, final Transceiver transceiver, final InvocationHashGenerator invocationHashGenerator, final InvocationCodec invocationCodec) {
        this(serverImplementationValidator, clientInterfaceValidator, timeoutScheduler, transceiver, invocationHashGenerator, invocationCodec, "transceiver");
    }

    public TransceiverTransport(final ServerImplementationValidator serverImplementationValidator, final ClientInterfaceValidator clientInterfaceValidator, final TimeoutScheduler timeoutScheduler, final Transceiver transceiver, final InvocationHashGenerator invocationHashGenerator, final InvocationCodec invocationCodec, final String transportName) {
        super(serverImplementationValidator, clientInterfaceValidator, timeoutScheduler, transportName);
        this.transceiver = transceiver;
        this.invocationHashGenerator = invocationHashGenerator;
        this.invocationCodec = invocationCodec;
    }

    private class TransceiverTransportInvocationHandler implements TransportInvocationHandler {
        private final EndpointName endpointName;
        private final Class<?> interfaceClass;
        private final Map<Method, byte[]> methodsToHashMap;

        public <T> TransceiverTransportInvocationHandler(final EndpointName endpointName, final Class<T> interfaceClass) {
            this.endpointName = endpointName;
            this.interfaceClass = interfaceClass;
            this.methodsToHashMap = invocationCodec.getMethodsToHashMap(endpointName, interfaceClass);
        }

        @Override
        public void invoke(final Method method, final Object[] args, final CompletableFuture<Object> future, final LinkedList<Runnable> timeoutRunnables) {
            // An invocation from the client is to be encoded, and sent to the server.
            final byte[] hash = methodsToHashMap.get(method);
            // Allocate a sequence number for this call and register as an outstanding call.
            final int thisSequence = sequence.incrementAndGet();
            outstandingMethodCalls.put(thisSequence, future);
            // TODO METRIC increment number of outstanding method calls
            timeoutRunnables.addFirst(() -> {
                outstandingMethodCalls.remove(thisSequence);
                // TODO METRIC decrement number of outstanding method calls
            });

            final List<ByteBuffer> bytes = invocationCodec.generateHashedMethodInvocation(thisSequence, hash, args);
            try {
                transceiver.getServerTransceiver().writeBuffer(bytes);
            } catch (IOException e) {
                logger.warn("Could not write buffer to server transceiver: " + e.getMessage());
            }
        }
    }

    @Override
    protected <T> TransportInvocationHandler createTransportInvocationHandler(final EndpointName endpointName, final Class<T> interfaceClass, final long methodTimeoutMilliseconds) {
        final Map<Method, byte[]> methodMap = invocationHashGenerator.generate(interfaceClass);

        // Register hashes...
        final Optional<InvocationCodec.EndpointInterfaceMethod> collidingEndpointInterfaceMethod = invocationCodec.registerHashes(endpointName, interfaceClass, methodMap);
        if (collidingEndpointInterfaceMethod.isPresent()) {
            throw new RegistrationException("Method hash collision when registering (Endpoint '" + endpointName +
                    "', Client interface '" + interfaceClass.getSimpleName() + "') conflicts with (" + collidingEndpointInterfaceMethod.get().toString() + ")");
        }

        return new TransceiverTransportInvocationHandler(endpointName, interfaceClass);
    }

    @Override
    public void start() {
        super.start();
        transceiver.open();
        // TODO how do incoming server responses get decoded and dispatched to the server impl?
    }

    @Override
    public void stop() {
        try {
            transceiver.close();
        } catch (final IOException e) {
            logger.warn("Could not close transceiver: " + e.getMessage());
        }
        super.stop();
    }

    int getNumberOfOutstandingMethodCalls() {
        return outstandingMethodCalls.size();
    }
}
