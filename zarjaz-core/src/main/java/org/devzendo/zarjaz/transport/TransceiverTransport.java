package org.devzendo.zarjaz.transport;

import org.devzendo.commoncode.string.HexDump;
import org.devzendo.zarjaz.nio.ReadableByteBuffer;
import org.devzendo.zarjaz.protocol.ByteBufferDecoder;
import org.devzendo.zarjaz.protocol.InvocationCodec;
import org.devzendo.zarjaz.protocol.Protocol;
import org.devzendo.zarjaz.reflect.InvocationHashGenerator;
import org.devzendo.zarjaz.reflect.MethodCallTimeoutHandlers;
import org.devzendo.zarjaz.reflect.MethodReturnTypeResolver;
import org.devzendo.zarjaz.timeout.TimeoutScheduler;
import org.devzendo.zarjaz.transceiver.Transceiver;
import org.devzendo.zarjaz.transceiver.TransceiverObservableEvent;
import org.devzendo.zarjaz.transceiver.TransceiverObserver;
import org.devzendo.zarjaz.util.BufferDumper;
import org.devzendo.zarjaz.validation.ClientInterfaceValidator;
import org.devzendo.zarjaz.validation.ServerImplementationValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Consumer;
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
    private final OutstandingMethodCalls outstandingMethodCalls;
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
        final MethodReturnTypeResolver typeResolver = new MethodReturnTypeResolver();
        this.outstandingMethodCalls = new OutstandingMethodCalls();
        this.serverResponseTransceiverObserver = new ServerResponseTransceiverObserver(outstandingMethodCalls, typeResolver);
        this.serverRequestTransceiverObserver = new ServerRequestTransceiverObserver(invocationCodec, this::getImplementation, typeResolver);
    }

    /*
     * Client side. Handle responses from servers; decodes and sets in the outstanding method calls map.
     */
    static class ServerResponseTransceiverObserver implements TransceiverObserver {
        private final OutstandingMethodCalls outstandingMethodCalls;
        private final MethodReturnTypeResolver typeResolver;

        public ServerResponseTransceiverObserver(final OutstandingMethodCalls outstandingMethodCalls,
                                                 final MethodReturnTypeResolver typeResolver) {
            this.outstandingMethodCalls = outstandingMethodCalls;
            this.typeResolver = typeResolver;
        }

        @Override
        public void eventOccurred(final TransceiverObservableEvent observableEvent) {
            // TODO test for null
            if (observableEvent.isFailure()) {
                // TODO test for failures
            } else {
                final List<ReadableByteBuffer> buffers = observableEvent.getData();
                // TODO test for null buffers, empty buffers
                // TODO use the InvocationCodec to parse the incoming data, and return a DecodedFrame subtype
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

        // Normal single-return method result processing:
        // Remove outstanding method call if it exists.
        // Decode the return type from the decoder
        // Set the future, unblocking.
        //
        // Custom multiple-return method result processing:
        // Do not remove outstanding method call if it exists.
        // Decode the return type from the decoder - must not be a future
        // Call the supplied multiple-invocation return processor.
        // Set the future in the timeout handler with a non-exceptional value (null?). Clients won't use this.
        //
        // This multiple-invocation method result processing will customise both Timeout and Transport timeout handlers.
        private void processMethodReturnResult(final ByteBufferDecoder decoder) throws IOException {
            final int sequence = decoder.readInt();
            // This may be a single- or multiple-return method.
            // Pass the return value on to the OutstandingMethodCalls, as this knows whether the call is single- or
            // multiple return.
            // The difference is that single return calls are removed from the outstanding calls when the return arrives
            // but multiple return calls are not removed then, but are removed on timeout.
            // TODO PERFORMANCE - this locks on the outstandingmethodcalls twice, for the two calls...
            try {
                final OutstandingMethodCalls.OutstandingMethodCall outstandingMethodCall = outstandingMethodCalls.get(sequence);
                final Object returnValue = decodeReturnValue(decoder, outstandingMethodCall);
                outstandingMethodCalls.resultReceived(sequence, returnValue);
            } catch (final SequenceNotFoundException se) {
                throw new IOException(se.getMessage(), se);
            }
            // TODO METRIC increment successful method decode
            // TODO REQUEST/RESPONSE LOGGING log method return
        }

        private Object decodeReturnValue(final ByteBufferDecoder decoder, final OutstandingMethodCalls.OutstandingMethodCall outstandingMethodCall) throws IOException {
            final Method method = outstandingMethodCall.getMethod();
            final Class<?> returnType = method.getReturnType();
            if (returnType.isAssignableFrom(Future.class)) {
                final Class<?> genericReturnType = typeResolver.getReturnType(method);
                return decoder.readObject(genericReturnType);
            } else {
                return decoder.readObject(returnType);
            }
        }
    }

    /*
     * Server side. Handle requests from clients; decodes, calls implementation, send response.
     */
    static class ServerRequestTransceiverObserver implements TransceiverObserver {
        private final InvocationCodec invocationCodec;
        private final Function<NamedInterface, Object> lookupImplementation;
        private final MethodReturnTypeResolver typeResolver;

        public ServerRequestTransceiverObserver(final InvocationCodec invocationCodec, final Function<NamedInterface, Object> lookupImplementation,
                                                final MethodReturnTypeResolver typeResolver) {
            this.invocationCodec = invocationCodec;
            this.lookupImplementation = lookupImplementation;
            this.typeResolver = typeResolver;
        }

        @Override
        public void eventOccurred(final TransceiverObservableEvent observableEvent) {
            logger.debug("Server has received a request from a client");
            if (observableEvent.isFailure()) {
                // TODO test for this
            } else {
                final List<ReadableByteBuffer> buffers = observableEvent.getData();
                // TODO METRIC empty list?
                // TODO rate limiting?
                try {
                    logger.debug("Decoding buffers");
                    final Optional<InvocationCodec.DecodedFrame> decodedFrame = invocationCodec.decodeFrame(buffers);
                    if (decodedFrame.isPresent()) {

                        // TODO convert to visitor
                        final InvocationCodec.DecodedFrame frame = decodedFrame.get();
                        logger.debug("Incoming frame: " + frame.getClass().getSimpleName());
                        if (frame instanceof InvocationCodec.HashedMethodInvocation) {
                            final InvocationCodec.HashedMethodInvocation hmi = (InvocationCodec.HashedMethodInvocation) frame;
                            processHashedMethodInvocation(observableEvent.getReplyWriter(), hmi.sequence, hmi.endpointInterfaceMethod.getEndpointName(), hmi.endpointInterfaceMethod.getClientInterface(), hmi.endpointInterfaceMethod.getMethod(), hmi.args);
                        }
                    } else {
                        // TODO METRIC increment bad/unsupported incoming frames
                        // TODO test for bad incoming frames
                        logger.error("Unsupported incoming frame");
                    }
                } catch (final Exception e) {

                    // TODO METRIC invocation failure
                    // TODO reply with failure
//                    final List<ByteBuffer> failure = invocationCodec.generateMethodFailureResponse();
//                    observableEvent.getServerTransceiver().writeBuffer(failure);
                    logger.warn("Invocation failure: " + e.getMessage(), e);
                }
                // Allow other observers of this to process it.
                for (final ReadableByteBuffer readableByteBuffer : buffers) {
                    readableByteBuffer.raw().rewind();
                }
            }
        }

        private void processHashedMethodInvocation(final Transceiver.BufferWriter replyTransceiver, final int sequence, final EndpointName endpointName, final Class<?> clientInterface, final Method method, final Object[] args) throws InvocationTargetException, IllegalAccessException, IOException {
            // TODO server request logging
            final NamedInterface namedInterface = new NamedInterface(endpointName, clientInterface);
            final Object implementation = lookupImplementation.apply(namedInterface);
            logger.debug("Invoking [" + endpointName + "] implementation " + implementation.getClass().getName() + " method " + method + " sequence " + sequence);
            final Object result = method.invoke(implementation, args);
            // TODO server response generation logging
            // TODO METRIC method duration timing
            final Class<?> returnType = method.getReturnType();
            if (method.getReturnType().isAssignableFrom(Future.class)) {
                final Class genericReturnType = typeResolver.getReturnType(method);
                logger.debug("Replying with generic return type " + genericReturnType);
                final CompletableFuture<?> future = (CompletableFuture<?>) result;
                future.whenComplete((completedValue, throwable) -> {
                    final List<ReadableByteBuffer> resultBuffers = invocationCodec.generateMethodReturnResponse(sequence, genericReturnType, completedValue);
                    logger.debug("Replying future contents to requestor");
                    try {
                        replyTransceiver.writeBuffer(resultBuffers);
                    } catch (final IOException e) {
                        logger.warn("Could not reply with future contents: " + e.getMessage(), e);
                        // TODO METRIC invocation failure
                        // TODO reply with failure
                    }
                });
            } else {
                final List<ReadableByteBuffer> resultBuffers = invocationCodec.generateMethodReturnResponse(sequence, returnType, result);
                logger.debug("Replying to requestor");
                replyTransceiver.writeBuffer(resultBuffers);
            }
        }
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
        public void invoke(final Method method, final Object[] args, final CompletableFuture<Object> future, final Optional<Consumer<Object>> consumer, final MethodCallTimeoutHandlers timeoutHandlers) {
            // An invocation from the client is to be encoded, and sent to the server.
            final byte[] hash = methodsToHashMap.get(method);

            // Allocate a sequence number for this call and register as an outstanding call.
            final int thisSequence = outstandingMethodCalls.put(new OutstandingMethodCalls.OutstandingMethodCall(hash, method, future, consumer));
            if (logger.isDebugEnabled()) {
                logger.debug("Invoking [" + endpointName + "] " + method.getDeclaringClass().getName() + "." + method.getName() + " hash " + HexDump.bytes2hex(hash) + " sequence " + thisSequence);
            }

            // TODO METRIC increment number of outstanding method calls
            timeoutHandlers.setTimeoutTransportHandler((f, en, m) -> {
                outstandingMethodCalls.remove(thisSequence);
                // TODO METRIC decrement number of outstanding method calls
            });

            final List<ReadableByteBuffer> bytes = invocationCodec.generateHashedMethodInvocation(thisSequence, endpointName, interfaceClass, method, args);
            if (logger.isDebugEnabled()) {
                logger.debug("Hashed method invocation:");
                BufferDumper.dumpBuffers(bytes);
            }
            try {
                transceiver.getServerWriter().writeBuffer(bytes);
            } catch (final IOException e) {
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

    private <T> void registerHashes(final EndpointName endpointName, final Class<T> interfaceClass) {
        final Map<Method, byte[]> methodMap = invocationHashGenerator.generate(endpointName, interfaceClass);

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
            transceiver.getClientEnd().addTransceiverObserver(serverResponseTransceiverObserver);
        }
        if (hasServerImplementationsBound()) {
            // On the server side, listen for incoming requests from the client
            logger.debug("Listening for incoming requests from the client");
            transceiver.getServerEnd().addTransceiverObserver(serverRequestTransceiverObserver);
        }
        try {
            // TODO this is the only point at which the transceiver is opened. if initial connection fails, it silently stays that way
            // with all dynamic proxy method calls timing out - these should re-trigger a transceiver open, if it isn't open.
            transceiver.open();
        } catch (final IOException e) {
            logger.error("Could not open transceiver: " + e.getMessage(), e);
        }
        // TODO how do incoming server responses get decoded and dispatched to the server impl?
    }

    @Override
    public void stop() {
        try {
            transceiver.getClientEnd().removeTransceiverObserver(serverResponseTransceiverObserver);
            transceiver.getServerEnd().removeTransceiverObserver(serverRequestTransceiverObserver);
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
