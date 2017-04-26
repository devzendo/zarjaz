package org.devzendo.zarjaz.e2e;

import org.apache.log4j.BasicConfigurator;
import org.devzendo.zarjaz.transceiver.UDPTransceiver;
import org.devzendo.zarjaz.transport.EndpointName;
import org.devzendo.zarjaz.transport.TransceiverTransportFactory;
import org.devzendo.zarjaz.transport.Transport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

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
public class TestRoundTripUDP {
    private static final Logger logger = LoggerFactory.getLogger(TestRoundTripUDP.class);
    private static final int PORT = 9877;
    private final EndpointName endpointName = new EndpointName("MyEndpoint");

    private Transport serverTransport;

    // TODO need to validate interfaces for public - if you change this to private, the round trip test fails,
    // saying 'can't call method in impl with public abstract' - even though there's no abstract involved!
    public interface Increment {
        int increment(int input);
    }

    private class IncrementServer implements Increment {
        @Override
        public int increment(int input) {
            return input + 1;
        }
    }

    @Before
    public void startServer() throws IOException {
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure();
        final UDPTransceiver serverTransceiver = UDPTransceiver.createServer(new InetSocketAddress(PORT));
        serverTransport = new TransceiverTransportFactory().create(serverTransceiver);
        serverTransport.registerServerImplementation(endpointName, Increment.class, new IncrementServer());
        serverTransport.start();
    }

    @After
    public void stopServer() {
        serverTransport.stop();
    }

    @Test
    public void sendIncrementMessages() throws IOException {
        final UDPTransceiver clientTransceiver = UDPTransceiver.createClient(new InetSocketAddress(PORT), false);
        final Transport clientTransport = new TransceiverTransportFactory().create(clientTransceiver);
        final Increment incProxy = clientTransport.createClientProxy(endpointName, Increment.class, 500);
        logger.info("Starting transport");
        clientTransport.start();
        try {
            for (int i = 0; i < 1000; i++) {
                int incremented = incProxy.increment(i);
                assertThat(incremented, equalTo(i + 1));
            }
        } finally {
            clientTransport.stop();
        }
    }
}

