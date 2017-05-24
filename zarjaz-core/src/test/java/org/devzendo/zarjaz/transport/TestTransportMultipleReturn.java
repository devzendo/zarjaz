package org.devzendo.zarjaz.transport;

import org.devzendo.zarjaz.logging.ConsoleLoggingUnittestCase;
import org.devzendo.zarjaz.protocol.DefaultInvocationCodec;
import org.devzendo.zarjaz.reflect.DefaultInvocationHashGenerator;
import org.devzendo.zarjaz.sample.primes.PrimeGenerator;
import org.devzendo.zarjaz.timeout.TimeoutScheduler;
import org.devzendo.zarjaz.transceiver.NullTransceiver;
import org.devzendo.zarjaz.validation.ClientInterfaceValidator;
import org.devzendo.zarjaz.validation.ServerImplementationValidator;
import org.hamcrest.Matchers;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import static java.util.Collections.synchronizedList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

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
public class TestTransportMultipleReturn extends ConsoleLoggingUnittestCase {
    private static final Logger logger = LoggerFactory.getLogger(TestTransportMultipleReturn.class);

    private static final EndpointName primesEndpointName = new EndpointName("primes");
    private Transport clientTransport;
    private Transport serverTransport1;
    private Transport serverTransport2;

    @Mock
    ClientInterfaceValidator clientValidator;

    @Mock
    ServerImplementationValidator serverValidator;

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() {
        final TimeoutScheduler timeoutScheduler = new TimeoutScheduler();
        final DefaultInvocationCodec invocationCodec = new DefaultInvocationCodec();
        final DefaultInvocationHashGenerator invocationHashGenerator = new DefaultInvocationHashGenerator();

        // The key to this test is that it uses the null transceiver, which will dispatch incoming data to all
        // registered listeners...
        final NullTransceiver nullTransceiver = new NullTransceiver();

        // ... of which we have two:
        serverTransport1 = new TransceiverTransport(serverValidator, clientValidator, timeoutScheduler, nullTransceiver, invocationHashGenerator, invocationCodec);
        serverTransport2 = new TransceiverTransport(serverValidator, clientValidator, timeoutScheduler, nullTransceiver, invocationHashGenerator, invocationCodec);
        // ... and a single client:
        clientTransport = new TransceiverTransport(serverValidator, clientValidator, timeoutScheduler, nullTransceiver, invocationHashGenerator, invocationCodec);
    }

    @After
    public void tearDown() {
        if (clientTransport != null) {
            clientTransport.stop();
        }
        if (serverTransport1 != null) {
            serverTransport1.stop();
        }
        if (serverTransport2 != null) {
            serverTransport2.stop();
        }
    }

    private static final class NamedPrimeGenerator implements PrimeGenerator {
        private final int[] primes = {2, 3, 5, 7, 9, 11, 13, 17, 19}; // etc., etc...
        private int primeIndex = 8;
        private final String serverName;

        public NamedPrimeGenerator(final String serverName) {
            this.serverName = serverName;
        }

        @Override
        public synchronized String generateNextPrimeMessage(final String userName) {
            if (primeIndex == 8) {
                primeIndex = 0;
            } else {
                primeIndex++;
            }
            return "Response from " + serverName + ": Hello " + userName + ", the next prime is " + primes[primeIndex];
        }

        @Override
        public Future<String> generateNextPrimeMessageAsynchronously(final String userName) {
            return null;
        }
    }

    // TODO cannot call createClientMultipleReturnInvoker before calling start

    // TODO cannot call createClientMultipleReturnInvoker with a method not part of the supplied interface

    @Ignore
    @Test(timeout = 2000)
    public void multipleReturn() {
        serverTransport1.registerServerImplementation(primesEndpointName, PrimeGenerator.class, new NamedPrimeGenerator("Dave"));
        serverTransport1.start();
        serverTransport2.registerServerImplementation(primesEndpointName, PrimeGenerator.class, new NamedPrimeGenerator("Jenny"));
        serverTransport2.start();

        final Method method = PrimeGenerator.class.getDeclaredMethods()[0];
        final List<String> returns = synchronizedList(new ArrayList<>());
        final MultipleReturnInvoker<PrimeGenerator> multipleReturnInvoker = clientTransport.createClientMultipleReturnInvoker(primesEndpointName, PrimeGenerator.class, 500L);

        clientTransport.start();

        final long startTime = System.currentTimeMillis();
        multipleReturnInvoker.<String>invoke(method, returns::add, 500L, "Matt");
        final long stopTime = System.currentTimeMillis();

        assertThat(stopTime - startTime, allOf(greaterThanOrEqualTo(500L), lessThan(1000L)));
        // 1000L is a reasonable upper limit, 500L is hard limit - mustn't take less than the method timeout.

        assertThat(returns, hasSize(2));
        assertThat(returns, Matchers.containsInAnyOrder(
                "Response from Dave: Hello Matt, the next prime is 2",
                "Response from Jenny: Hello Matt, the next prime is 2"
        ));
    }

    @Test
    public void multipleReturnCallValidatesClientInterface() {
        clientTransport.createClientMultipleReturnInvoker(primesEndpointName, PrimeGenerator.class, 500L);
        Mockito.verify(clientValidator, Mockito.times(1)).validateClientInterface(PrimeGenerator.class);
    }

    @Test
    public void multipleReturnThrowsOnStartIfNoMultipleReturnInvokerCreated() {
        // register a server, so that the test checks for client interfaces
        serverTransport1.registerServerImplementation(primesEndpointName, PrimeGenerator.class, new NamedPrimeGenerator("Dave"));

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("No clients or server implementations bound");
        clientTransport.start();
    }

    @Test
    public void multipleReturnRegistersClientInterfaceWhenMultipleReturnInvokerCreated() {
        // register a server, so that the test checks for client interfaces
        serverTransport1.registerServerImplementation(primesEndpointName, PrimeGenerator.class, new NamedPrimeGenerator("Dave"));

        clientTransport.createClientMultipleReturnInvoker(primesEndpointName, PrimeGenerator.class, 500L);
        clientTransport.start();
        // all ok, no exception
    }
}
