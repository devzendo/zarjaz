package org.devzendo.zarjaz.transport;

import org.devzendo.commoncode.string.HexDump;
import org.devzendo.zarjaz.protocol.ByteBufferDecoder;
import org.devzendo.zarjaz.protocol.InvocationCodec;
import org.devzendo.zarjaz.protocol.Protocol;
import org.devzendo.zarjaz.reflect.InvocationHashGenerator;
import org.devzendo.zarjaz.timeout.TimeoutScheduler;
import org.devzendo.zarjaz.transceiver.Transceiver;
import org.devzendo.zarjaz.transceiver.TransceiverObservableEvent;
import org.devzendo.zarjaz.transceiver.TransceiverObserver;
import org.devzendo.zarjaz.validation.ClientInterfaceValidator;
import org.devzendo.zarjaz.validation.ServerImplementationValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

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
    static class OutstandingMethodCall {
        public final byte[] hash;
        public final Method method;
        public final CompletableFuture<Object> future;

        public OutstandingMethodCall(final byte[] hash, final Method method, final CompletableFuture<Object> future) {
            this.hash = hash;
            this.method = method;
            this.future = future;
        }
    }
    private final Map<Integer, OutstandingMethodCall> outstandingMethodCalls = new ConcurrentHashMap<>();
    private final TransceiverObserver serverResponseTransceiverObserver;
    private final TransceiverObserver serverRequestTransceiverObserver;

    public TransceiverTransport(final ServerImplementationValidator serverImplementationValidator, final ClientInterfaceValidator clientInterfaceValidator, final TimeoutScheduler timeoutScheduler, final Transceiver transceiver, final InvocationHashGenerator invocationHashGenerator, final InvocationCodec invocationCodec) {
        this(serverImplementationValidator, clientInterfaceValidator, timeoutScheduler, transceiver, invocationHashGenerator, invocationCodec, "transceiver");
    }

    public TransceiverTransport(final ServerImplementationValidator serverImplementationValidator, final ClientInterfaceValidator clientInterfaceValidator, final TimeoutScheduler timeoutScheduler, final Transceiver transceiver, final InvocationHashGenerator invocationHashGenerator, final InvocationCodec invocationCodec, final String transportName) {
        super(serverImplementationValidator, clientInterfaceValidator, timeoutScheduler, transportName);
        this.transceiver = transceiver;
        this.invocationHashGenerator = invocationHashGenerator;
        this.invocationCodec = invocationCodec;
        this.serverResponseTransceiverObserver = new ServerResponseTransceiverObserver(outstandingMethodCalls);
        this.serverRequestTransceiverObserver = new ServerRequestTransceiverObserver(invocationCodec, this::getImplementation);
    }

    /*
     * Client side. Handle responses from servers; decodes and sets in the outstanding method calls map.
     */
    static class ServerResponseTransceiverObserver implements TransceiverObserver {
        private final Map<Integer, OutstandingMethodCall> outstandingMethodCalls;

        public ServerResponseTransceiverObserver(final Map<Integer, OutstandingMethodCall> outstandingMethodCalls) {
            this.outstandingMethodCalls = outstandingMethodCalls;
        }

        @Override
        public void eventOccurred(final TransceiverObservableEvent observableEvent) {
            // TODO test for null
            if (observableEvent.isFailure()) {
                // TODO test for failures
            } else {
                final List<ByteBuffer> buffers = observableEvent.getData();
                // TODO test for null buffers, empty buffers
                final ByteBufferDecoder decoder = new ByteBufferDecoder(buffers);
                try {
                    final byte initialFrameByte = decoder.readByte();
                    processFrame(initialFrameByte, decoder);
                } catch (final IOException e) {
                    // TODO METRIC increment bad incoming frames
                    // TODO test for this
                    logger.warn("Incoming frame decode: " + e.getMessage(), e);
                }
            }
        }

        private void processFrame(final byte initialFrameByte, final ByteBufferDecoder decoder) throws IOException {
            if (initialFrameByte == Protocol.InitialFrameType.METHOD_RETURN_RESULT.getInitialFrameType()) {
                processMethodReturnResult(decoder);
            } else {
                // TODO METRIC increment unsupported incoming frames
                // TODO test for this
                throw new IOException("Unsupported incoming frame type 0x" + HexDump.byte2hex(initialFrameByte));
            }
        }

        private void processMethodReturnResult(final ByteBufferDecoder decoder) throws IOException {
            final int sequence = decoder.readInt();
            final OutstandingMethodCall outstandingMethodCall = outstandingMethodCalls.remove(sequence);
            if (outstandingMethodCall == null) {
                // TODO test for this
                throw new IOException("Incoming method return with sequence " + sequence + " is not outstanding");
            }
            final Class<?> returnType = outstandingMethodCall.method.getReturnType();
            final Object returnValue = decoder.readObject(returnType);
            outstandingMethodCall.future.complete(returnValue);
            // TODO METRIC increment successful method decode
            // TODO REQUEST/RESPONSE LOGGING log method return
        }
    }

    /*
     * Server side. Handle requests from clients; decodes, calls implementation, send response.
     */
    static class ServerRequestTransceiverObserver implements TransceiverObserver {
        private final InvocationCodec invocationCodec;
        private final Function<NamedInterface, Object> lookupImplementation;

        public ServerRequestTransceiverObserver(final InvocationCodec invocationCodec, final Function<NamedInterface, Object> lookupImplementation) {
            this.invocationCodec = invocationCodec;
            this.lookupImplementation = lookupImplementation;
        }

        @Override
        public void eventOccurred(final TransceiverObservableEvent observableEvent) {
            logger.debug("Server has received a request from a client");
            if (observableEvent.isFailure()) {
                // TODO test for this
            } else {
                final List<ByteBuffer> buffers = observableEvent.getData();
                // TODO METRIC empty list?
                // TODO rate limiting?
                try {
                    logger.debug("Decoding buffers");
                    final InvocationCodec.DecodedFrame decodedFrame = invocationCodec.decodeFrame(buffers);

                    // TODO convert to visitor
                    if (decodedFrame instanceof InvocationCodec.HashedMethodInvocation) {
                        final InvocationCodec.HashedMethodInvocation hmi = (InvocationCodec.HashedMethodInvocation) decodedFrame;
                        processHashedMethodInvocation(observableEvent.getServerTransceiver(), hmi.sequence, hmi.endpointInterfaceMethod.getEndpointName(), hmi.endpointInterfaceMethod.getClientInterface(), hmi.endpointInterfaceMethod.getMethod(), hmi.args);
                    }
                } catch (final Exception e) {
                    // TODO METRIC invocation failure
                    // TODO reply with failure
//                    final List<ByteBuffer> failure = invocationCodec.generateMethodFailureResponse();
//                    observableEvent.getServerTransceiver().writeBuffer(failure);
                    logger.warn("Invocation failure: " + e.getMessage(), e);
                }
                /*

                final ByteBufferDecoder decoder = new ByteBufferDecoder(buffers);
                try {
                    final byte initialFrameByte = decoder.readByte();
                    processFrame(observableEvent.getServerTransceiver(), initialFrameByte, decoder);
                } catch (final IOException e) {
                    // TODO METRIC increment bad incoming frames
                    // TODO test for this
                    logger.warn("Incoming frame decode: " + e.getMessage(), e);
                }
                 */
            }
        }

        private void processHashedMethodInvocation(final Transceiver.ServerTransceiver replyTransceiver, final int sequence, final EndpointName endpointName, final Class<?> clientInterface, final Method method, final Object[] args) throws InvocationTargetException, IllegalAccessException, IOException {
            // TODO server request logging
            final NamedInterface namedInterface = new NamedInterface(endpointName, clientInterface);
            final Object implementation = lookupImplementation.apply(namedInterface);
            final Object result = method.invoke(implementation, args);
            // TODO server response generation logging
            // TODO METRIC method duration timing
            final List<ByteBuffer> resultBuffers = invocationCodec.generateMethodReturnResponse(sequence, method.getReturnType(), result);
            replyTransceiver.writeBuffer(resultBuffers);
        }
/*
        private void processFrame(final Transceiver.ServerTransceiver replyTransceiver, final byte initialFrameByte, final ByteBufferDecoder decoder) throws IOException {
            if (initialFrameByte == Protocol.InitialFrameType.METHOD_INVOCATION_HASHED.getInitialFrameType()) {
                processHashedMethodInvocation(replyTransceiver, decoder);
            } else {
                // TODO METRIC increment unsupported incoming frames
                // TODO test for this
                throw new IOException("Unsupported incoming frame type 0x" + HexDump.byte2hex(initialFrameByte));
            }
        }

        private void processHashedMethodInvocation(final Transceiver.ServerTransceiver replyTransceiver, final ByteBufferDecoder decoder) {
            // need to look up impl via final Map<NamedInterface, Object> implementations = new HashMap<>();

            final InvocationCodec.HashedMethodInvocation hashedMethodInvocation = invocationCodec.decodeHashedMethodInvocation(decoder);

        }
*/
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
            outstandingMethodCalls.put(thisSequence, new OutstandingMethodCall(hash, method, future));
            // TODO METRIC increment number of outstanding method calls
            timeoutRunnables.addFirst(() -> {
                outstandingMethodCalls.remove(thisSequence);
                // TODO METRIC decrement number of outstanding method calls
            });

            final List<ByteBuffer> bytes = invocationCodec.generateHashedMethodInvocation(thisSequence, endpointName, interfaceClass, method, args);
            try {
                transceiver.getServerTransceiver().writeBuffer(bytes);
            } catch (IOException e) {
                logger.warn("Could not write buffer to server transceiver: " + e.getMessage());
            }
        }
    }

    @Override
    protected <T> void registerTransportRequestDispatcher(final EndpointName endpointName, final Class<T> interfaceClass) {
        registerHashes(endpointName, interfaceClass);
    }

    @Override
    protected <T> TransportInvocationHandler createTransportInvocationHandler(final EndpointName endpointName, final Class<T> interfaceClass, final long methodTimeoutMilliseconds) {
        registerHashes(endpointName, interfaceClass);

        return new TransceiverTransportInvocationHandler(endpointName, interfaceClass);
    }

    private <T> void registerHashes(EndpointName endpointName, Class<T> interfaceClass) {
        final Map<Method, byte[]> methodMap = invocationHashGenerator.generate(interfaceClass);

        // Register hashes...
        final Optional<InvocationCodec.EndpointInterfaceMethod> collidingEndpointInterfaceMethod = invocationCodec.registerHashes(endpointName, interfaceClass, methodMap);
        if (collidingEndpointInterfaceMethod.isPresent()) {
            throw new RegistrationException("Method hash collision when registering (Endpoint '" + endpointName +
                    "', Client interface '" + interfaceClass.getSimpleName() + "') conflicts with (" + collidingEndpointInterfaceMethod.get().toString() + ")");
        }
    }

    @Override
    public void start() {
        super.start();
        if (hasClientProxiesBound()) {
            // On the client side, listen for incoming responses from the server
            logger.debug("Listening for incoming responses from the server");
            transceiver.getClientTransceiver().addTransceiverObserver(serverResponseTransceiverObserver);
        }
        if (hasServerImplementationsBound()) {
            // On the server side, listen for incoming requests from the client
            logger.debug("Listening for incoming requests from the client");
            transceiver.getClientTransceiver().addTransceiverObserver(serverRequestTransceiverObserver);
        }
        transceiver.open();
        // TODO how do incoming server responses get decoded and dispatched to the server impl?
    }

    @Override
    public void stop() {
        try {
            transceiver.getClientTransceiver().removeTransceiverObserver(serverResponseTransceiverObserver);
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
