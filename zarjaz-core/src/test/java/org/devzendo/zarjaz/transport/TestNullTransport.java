package org.devzendo.zarjaz.transport;

import org.apache.log4j.BasicConfigurator;
import org.devzendo.zarjaz.sample.primes.DefaultPrimeGenerator;
import org.devzendo.zarjaz.sample.primes.PrimeGenerator;
import org.devzendo.zarjaz.transport.NullTransport;
import org.devzendo.zarjaz.transport.NullTransportFactory;
import org.devzendo.zarjaz.transport.Transport;
import org.devzendo.zarjaz.validation.ClientInterfaceValidator;
import org.devzendo.zarjaz.validation.ServerImplementationValidator;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnit44Runner;
import org.mockito.runners.MockitoJUnitRunner;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Copyright (C) 2008-2015 Matt Gumbley, DevZendo.org <http://devzendo.org>
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@RunWith(MockitoJUnitRunner.class)
public class TestNullTransport {

    {
    BasicConfigurator.configure();
    }

    final String userName = "Matt";

    @Mock ClientInterfaceValidator clientValidator;

    @Mock ServerImplementationValidator serverValidator;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    Transport nullTransport;

    @Before
    public void setUp() {
        nullTransport = new NullTransport(serverValidator, clientValidator);
    }

    @After
    public void tearDown() {
        if (nullTransport != null) {
            nullTransport.stop();
        }
    }

    @Test
    public void multipleServerImplementationRegistrationsWithSameNameDisallowed() {
        final DefaultPrimeGenerator serverImplementation = new DefaultPrimeGenerator();

        thrown.expect(RegistrationException.class);
        thrown.expectMessage("The EndpointName 'primes' is already registered");

        nullTransport.registerServerImplementation(new EndpointName("primes"), PrimeGenerator.class, serverImplementation);
        nullTransport.registerServerImplementation(new EndpointName("primes"), PrimeGenerator.class, serverImplementation);
    }

    @Test
    public void clientProxyRequestedForUnboundNameDisallowed() {
        // TODO
    }

    @Test
    public void roundTripWithoutTimeout() {
        final DefaultPrimeGenerator serverImplementation = new DefaultPrimeGenerator();

        nullTransport.registerServerImplementation(new EndpointName("primes"), PrimeGenerator.class, serverImplementation);
        Mockito.verify(clientValidator).validateClientInterface(PrimeGenerator.class);
        Mockito.verify(serverValidator).validateServerImplementation(PrimeGenerator.class, serverImplementation);

//        final PrimeGenerator clientProxy = nullTransport.createClientProxy("primes", PrimeGenerator.class);
//
//        nullTransport.start();
//
//        assertThat(clientProxy.generateNextPrimeMessage(userName), equalTo("Hello Matt, the next prime is 2"));
    }
}
