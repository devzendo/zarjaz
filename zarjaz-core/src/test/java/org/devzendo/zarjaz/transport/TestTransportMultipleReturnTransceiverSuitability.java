package org.devzendo.zarjaz.transport;

import org.devzendo.zarjaz.logging.ConsoleLoggingUnittestCase;
import org.devzendo.zarjaz.protocol.DefaultInvocationCodec;
import org.devzendo.zarjaz.reflect.DefaultInvocationHashGenerator;
import org.devzendo.zarjaz.sample.primes.PrimeGenerator;
import org.devzendo.zarjaz.timeout.TimeoutScheduler;
import org.devzendo.zarjaz.transceiver.*;
import org.devzendo.zarjaz.util.BroadcastAddressHelper;
import org.devzendo.zarjaz.validation.ClientInterfaceValidator;
import org.devzendo.zarjaz.validation.ServerImplementationValidator;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Collection;

import static java.util.Arrays.asList;
import static org.junit.Assume.assumeNotNull;

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
@RunWith(Parameterized.class)
public class TestTransportMultipleReturnTransceiverSuitability extends ConsoleLoggingUnittestCase {
    private static final Logger logger = LoggerFactory.getLogger(TestTransportMultipleReturnTransceiverSuitability.class);
    private static final EndpointName primesEndpointName = new EndpointName("primes");

    @BeforeClass
    public static void getBroadcastAddress() throws SocketException {
        setupLoggingStatically();

        broadcastAddress = BroadcastAddressHelper.getBroadcastAddress();
        // logging in here comes out at the end of the tests when run in intellij.
        if (broadcastAddress == null) {
            logger.info("No broadcast address available");
        } else {
            logger.info("Using broadcast address " + broadcastAddress);
        }
    }

    private static InetAddress broadcastAddress = null;

    private static class TestParameters {
        public ConnectionType connectionType;
        public Boolean suitableForMultipleReturn;

        public TestParameters(final ConnectionType connectionType, final boolean suitableForMultipleReturn) {
            this.connectionType = connectionType;
            this.suitableForMultipleReturn = suitableForMultipleReturn;
        }
    }

    @Parameterized.Parameters
    public static Collection<TestParameters> data() {
        return asList(
                new TestParameters(ConnectionType.NULL, true),
                new TestParameters(ConnectionType.UDP, false),
                new TestParameters(ConnectionType.TCP, false),
                new TestParameters(ConnectionType.UDP_BROADCAST, true));
    }

    private final DefaultInvocationCodec invocationCodec = new DefaultInvocationCodec();
    private final DefaultInvocationHashGenerator invocationHashGenerator = new DefaultInvocationHashGenerator();
    private final TestParameters testParameters;
    private Transport clientTransport;
    private Transceiver clientTransceiver;

    @Mock
    TimeoutScheduler timeoutScheduler;

    @Mock
    ClientInterfaceValidator clientValidator;

    @Mock
    ServerImplementationValidator serverValidator;

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    // runs before mocks initialised, so do real construction in @Before.
    public TestTransportMultipleReturnTransceiverSuitability(final TestParameters testParameters) {
        this.testParameters = testParameters;
    }

    @Before
    public void setupTransceiver() throws IOException {
        logger.info(">>>>> BEFORE ***** start of setupTransceiver, connection type " + testParameters.connectionType + " *****");
        switch (testParameters.connectionType) {
            case NULL:
                clientTransceiver = new NullTransceiver();
                break;
            case UDP:
                clientTransceiver = UDPTransceiver.createClient(new InetSocketAddress(9876), false);
                break;
            case TCP:
                clientTransceiver = TCPTransceiver.createClient(new InetSocketAddress(9876));
                break;
            case UDP_BROADCAST:
                assumeNotNull(broadcastAddress);
                clientTransceiver = UDPTransceiver.createClient(new InetSocketAddress(broadcastAddress, 9876), true);
                break;
        }
        logger.debug("<<<<< BEFORE ***** end of setupTransceiver *****");
    }

    private void setUp(final Transceiver transceiver) {
    }


    @After
    public void tearDown() {
        if (clientTransport != null) {
            clientTransport.stop();
        }
    }

    @Test
    public void supportsMultipleReturnOrNot() {
        if (!testParameters.suitableForMultipleReturn) {
            thrown.expect(IllegalStateException.class);
            thrown.expectMessage("The " + clientTransceiver + " does not support multiple return invocation");
        }
        clientTransport = new TransceiverTransport(serverValidator, clientValidator, timeoutScheduler, clientTransceiver, invocationHashGenerator, invocationCodec);
        clientTransport.createClientMultipleReturnInvoker(primesEndpointName, PrimeGenerator.class, 500L);
    }
}
