package org.devzendo.zarjaz.transport;

import org.devzendo.zarjaz.nio.ReadableByteBuffer;
import org.devzendo.zarjaz.protocol.ByteBufferEncoder;
import org.devzendo.zarjaz.protocol.DefaultInvocationCodec;
import org.devzendo.zarjaz.protocol.InvocationCodec;
import org.devzendo.zarjaz.reflect.DefaultInvocationHashGenerator;
import org.devzendo.zarjaz.reflect.MethodReturnTypeResolver;
import org.devzendo.zarjaz.transceiver.DataReceived;
import org.devzendo.zarjaz.transceiver.Transceiver;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
public class TestServerRequestTransceiverObserver {
    private static final int SEQUENCE = 69;

    private final InvocationCodec invocationCodec = new DefaultInvocationCodec();
    private final EndpointName endpointName = new EndpointName("endpoint1");
    private final EndpointName intentionallyMissingEndpoint = new EndpointName("IntentionallyMissing");
    private final ByteBufferEncoder encoder = new ByteBufferEncoder();
    private Map<Method, byte[]> methodMap;
    private Method addOneMethod;
    private Method intentionallyNotRegisteredMethod;
    private final Map<NamedInterface, Object> implementations = new HashMap<>();
    private final MethodReturnTypeResolver typeResolver = new MethodReturnTypeResolver();
    private final TransceiverTransport.ServerRequestTransceiverObserver observer = new TransceiverTransport.ServerRequestTransceiverObserver(invocationCodec, implementations::get, typeResolver);

    private interface SampleInterface {
        public int addOne(int input);
    }

    private class SampleImplementation implements SampleInterface {
        public int invocationCount = 0;
        public int addOne(int input) {
            invocationCount++;
            return input + 1;
        };
    }

    private class RecordingServerTransceiver implements Transceiver.BufferWriter {
        public List<List<ReadableByteBuffer>> received = new ArrayList<>();

        @Override
        public void writeBuffer(final List<ReadableByteBuffer> data) throws IOException {
            received.add(data);
        }
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void registerHashes() {
        methodMap = new DefaultInvocationHashGenerator().generate(endpointName, SampleInterface.class);
        invocationCodec.registerHashes(endpointName, SampleInterface.class, methodMap);
        addOneMethod = SampleInterface.class.getDeclaredMethods()[0];
        implementations.put(new NamedInterface(endpointName, SampleInterface.class), new SampleImplementation());
    }

    @Test
    public void receiveMethodInvocationArgumentMismatch() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Registered method argument count does not match method invocation argument count");

        final Object[] emptyArguments = new Object[0];
        invocationCodec.generateHashedMethodInvocation(SEQUENCE, endpointName, SampleInterface.class, addOneMethod, emptyArguments);
    }

    @Test
    public void receiveMethodInvocation() {
        final List<ReadableByteBuffer> invocation = invocationCodec.generateHashedMethodInvocation(SEQUENCE, endpointName, SampleInterface.class, addOneMethod, new Object[] { 5 });

        final RecordingServerTransceiver recorder = new RecordingServerTransceiver();
        observer.eventOccurred(new DataReceived(invocation, recorder));
    }
}
