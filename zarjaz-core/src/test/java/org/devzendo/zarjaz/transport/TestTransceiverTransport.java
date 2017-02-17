package org.devzendo.zarjaz.transport;

import org.apache.log4j.BasicConfigurator;
import org.devzendo.zarjaz.protocol.DefaultInvocationCodec;
import org.devzendo.zarjaz.protocol.InvocationCodec;
import org.devzendo.zarjaz.protocol.Protocol;
import org.devzendo.zarjaz.reflect.DefaultInvocationHashGenerator;
import org.devzendo.zarjaz.reflect.InvocationHashGenerator;
import org.devzendo.zarjaz.timeout.TimeoutScheduler;
import org.devzendo.zarjaz.transceiver.NullTransceiver;
import org.devzendo.zarjaz.transceiver.Transceiver;
import org.devzendo.zarjaz.transceiver.TransceiverObservableEvent;
import org.devzendo.zarjaz.validation.DefaultClientInterfaceValidator;
import org.devzendo.zarjaz.validation.DefaultServerImplementationValidator;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static org.devzendo.commoncode.concurrency.ThreadUtils.waitNoInterruption;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

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
public class TestTransceiverTransport {
    private static final int METHOD_TIMEOUT_MILLISECONDS = 500;

    {
        BasicConfigurator.configure();
    }

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private final Transceiver nullTransceiver = new NullTransceiver();
    private final List<TransceiverObservableEvent> clientToServerTransceiverObservableEvents = new ArrayList<>();
    private final DefaultServerImplementationValidator serverImplementationValidator = new DefaultServerImplementationValidator();
    private final DefaultClientInterfaceValidator clientInterfaceValidator = new DefaultClientInterfaceValidator();
    private final TimeoutScheduler timeoutScheduler = new TimeoutScheduler();
    private final InvocationCodec invocationCodec = new DefaultInvocationCodec();
    private final EndpointName endpointName = new EndpointName("MyEndpoint");
    private final InvocationHashGenerator invocationHashGenerator = new DefaultInvocationHashGenerator(endpointName);

    private TransceiverTransport transport;

    @After
    public void stopTransceiver() {
        if (transport != null) {
            transport.stop();
        }
    }

    private class IntentionallyCollidingInvocationHashGenerator implements InvocationHashGenerator {
        private byte[] fixedHash = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 };
        @Override
        public Map<Method, byte[]> generate(final Class<?> interfaceClass) {
            final Map<Method, byte[]> map = new HashMap<>();
            for (Method method: interfaceClass.getMethods()) {
                map.put(method, fixedHash);
            }
            return map;
        }
    }

    private void captureFromNullTransceiver() {
        nullTransceiver.getClientTransceiver().addTransceiverObserver(clientToServerTransceiverObservableEvents::add);
    }

    private interface SampleInterface {
        void someMethod();
    }

    @Test
    public void hashCollisionsAreDetected() {
        final Transport transport = new TransceiverTransport(serverImplementationValidator, clientInterfaceValidator,
                timeoutScheduler, nullTransceiver, new IntentionallyCollidingInvocationHashGenerator(), invocationCodec);
        transport.createClientProxy(new EndpointName("endpoint1"), SampleInterface.class, METHOD_TIMEOUT_MILLISECONDS);

        // Normally this would be fine since in the default hash invocation generator, endpoint names are part of the
        // hash. but the intentionally colliding one above does not check endpoints, and always returns a fixed hash.
        // Difficult to generate an endpoint/interface/method that would trigger this in the real implementation!
        thrown.expect(RegistrationException.class);
        thrown.expectMessage("Method hash collision when registering (Endpoint 'endpoint2', Client interface 'SampleInterface') conflicts with (Endpoint 'endpoint1', Client interface 'SampleInterface', Method 'someMethod')");

        transport.createClientProxy(new EndpointName("endpoint2"), SampleInterface.class, METHOD_TIMEOUT_MILLISECONDS);
    }

    @Test
    public void clientRequestIsEncodedAndSentToTransceiver() {
        captureFromNullTransceiver();
        transport = new TransceiverTransport(serverImplementationValidator, clientInterfaceValidator,
                timeoutScheduler, nullTransceiver, invocationHashGenerator, invocationCodec);
        try {
            final SampleInterface clientProxy = transport.createClientProxy(endpointName, SampleInterface.class, METHOD_TIMEOUT_MILLISECONDS);
            transport.start();

            clientProxy.someMethod();
            // the transport handler isn't going to get a response, so the completion will time out
            fail("A timeout should have happened");
        } catch (final MethodInvocationTimeoutException me) {
            // Check for a correctly encoded method request
            assertThat(clientToServerTransceiverObservableEvents, hasSize(1));
            final TransceiverObservableEvent event = clientToServerTransceiverObservableEvents.get(0);
            assertFalse(event.isFailure());
            final List<ByteBuffer> data = event.getData();
            assertThat(data.size(), equalTo(1));
            final ByteBuffer buffer = data.get(0);
            // not an exhaustive check on the buffer contents (don't know the hash)... this is done in TestInvocationCodec.
            assertThat(buffer.limit(), equalTo(21)); // frame type byte / sequence 4 bytes / hash 16 bytes / no args
            final byte[] frame = Arrays.copyOf(buffer.array(), 5);

            assertThat(frame, equalTo(new byte[]{
                    Protocol.InitialFrameType.METHOD_INVOCATION_HASHED.getInitialFrameType(),

                    // sequence
                    0,
                    0,
                    0,
                    1,

                    // hash....
            }));
        }
    }

    @Test
    public void clientRequestIncreasesOutstandingMethodCallCount() throws InterruptedException {
        transport = new TransceiverTransport(serverImplementationValidator, clientInterfaceValidator,
                timeoutScheduler, nullTransceiver, invocationHashGenerator, invocationCodec);
        try {
            final SampleInterface clientProxy = transport.createClientProxy(endpointName, SampleInterface.class, METHOD_TIMEOUT_MILLISECONDS);
            transport.start();

            // do on another thread... that will have the timeout exception thrown on it clientProxy.someMethod();
            // then counts down a latch
            final CountDownLatch aboutToCallOnOtherThread = new CountDownLatch(1);
            final CountDownLatch done = new CountDownLatch(1);
            new Thread(() -> {
                // the transport handler isn't going to get a response, so the completion will time out
                aboutToCallOnOtherThread.countDown();
                try {
                    clientProxy.someMethod();
                } catch (Exception e) {
                    // a timeout exception will be caught
                }
                done.countDown();
            }).start();

            aboutToCallOnOtherThread.await();
            waitNoInterruption(250); // give the thread time to make the call
            assertThat(transport.getNumberOfOutstandingMethodCalls(), equalTo(1));

            // wait for timeout to unlatch
            done.await();
            waitNoInterruption(250); // give the timeout handler time to remove the outstanding method call

            assertThat(transport.getNumberOfOutstandingMethodCalls(), equalTo(0));

        } catch (final MethodInvocationTimeoutException me) {
            // Check for a correctly encoded method request
            assertThat(clientToServerTransceiverObservableEvents, hasSize(1));
            final TransceiverObservableEvent event = clientToServerTransceiverObservableEvents.get(0);
            assertFalse(event.isFailure());
            final List<ByteBuffer> data = event.getData();
        }
    }

}
