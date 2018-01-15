package org.devzendo.zarjaz.transport;

import org.devzendo.commoncode.timeout.TimeoutScheduler;
import org.devzendo.zarjaz.sample.primes.DefaultPrimeGenerator;
import org.devzendo.zarjaz.sample.primes.PrimeGenerator;
import org.devzendo.zarjaz.validation.ClientInterfaceValidator;
import org.devzendo.zarjaz.validation.ServerImplementationValidator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

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
public class TestNullTransport {
    private static final EndpointName primesEndpointName = new EndpointName("primes");

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

    @Test
    public void doesNotSupportMultipleReturn() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("The NullTransport does not support multiple return invocation");

        final Transport clientTransport = new NullTransport(serverValidator, clientValidator, timeoutScheduler);
        clientTransport.registerServerImplementation(primesEndpointName, PrimeGenerator.class, new DefaultPrimeGenerator()); // needed for prior validation steps

        clientTransport.createClientMultipleReturnInvoker(primesEndpointName, PrimeGenerator.class, 500L);
    }
}
