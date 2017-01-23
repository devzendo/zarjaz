package org.devzendo.zarjaz.transport;

import org.apache.log4j.BasicConfigurator;
import org.devzendo.zarjaz.protocol.DefaultInvocationCodec;
import org.devzendo.zarjaz.protocol.InvocationCodec;
import org.devzendo.zarjaz.reflect.InvocationHashGenerator;
import org.devzendo.zarjaz.timeout.TimeoutScheduler;
import org.devzendo.zarjaz.transceiver.NullTransceiver;
import org.devzendo.zarjaz.transceiver.Transceiver;
import org.devzendo.zarjaz.validation.DefaultClientInterfaceValidator;
import org.devzendo.zarjaz.validation.DefaultServerImplementationValidator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.lang.reflect.Method;
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
public class TestTransceiverTransport {
    {
        BasicConfigurator.configure();
    }

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private final Transceiver nullTransceiver = new NullTransceiver();
    private final DefaultServerImplementationValidator serverImplementationValidator = new DefaultServerImplementationValidator();
    private final DefaultClientInterfaceValidator clientInterfaceValidator = new DefaultClientInterfaceValidator();
    private final TimeoutScheduler timeoutScheduler = new TimeoutScheduler();
    private final InvocationCodec invocationCodec = new DefaultInvocationCodec();

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

    private interface SampleInterface {
        void someMethod();
    }

    @Test
    public void hashCollisionsAreDetected() {
        final Transport transport = new TransceiverTransport(serverImplementationValidator, clientInterfaceValidator,
                timeoutScheduler, nullTransceiver, new IntentionallyCollidingInvocationHashGenerator(), invocationCodec);
        final int METHOD_TIMEOUT_MILLISECONDS = 500;
        transport.createClientProxy(new EndpointName("endpoint1"), SampleInterface.class, METHOD_TIMEOUT_MILLISECONDS);

        // Normally this would be fine since in the default hash invocation generator, endpoint names are part of the
        // hash. but the intentionally colliding one above does not check endpoints, and always returns a fixed hash.
        // Difficult to generate an endpoint/interface/method that would trigger this in the real implementation!
        thrown.expect(RegistrationException.class);
        thrown.expectMessage("Method hash collision when registering (Endpoint 'endpoint2', Client interface 'SampleInterface') conflicts with (Endpoint 'endpoint1', Client interface 'SampleInterface', Method 'someMethod')");

        transport.createClientProxy(new EndpointName("endpoint2"), SampleInterface.class, METHOD_TIMEOUT_MILLISECONDS);
    }

}
