package org.devzendo.zarjaz.transport;

import org.devzendo.commoncode.concurrency.ThreadUtils;
import org.devzendo.zarjaz.logging.ConsoleLoggingUnittestCase;
import org.devzendo.zarjaz.protocol.DefaultInvocationCodec;
import org.devzendo.zarjaz.reflect.DefaultInvocationHashGenerator;
import org.devzendo.zarjaz.sample.primes.DefaultPrimeGenerator;
import org.devzendo.zarjaz.sample.primes.PrimeGenerator;
import org.devzendo.zarjaz.sample.timeout.DefaultTimeoutGenerator;
import org.devzendo.zarjaz.sample.timeout.TimeoutGenerator;
import org.devzendo.zarjaz.timeout.TimeoutScheduler;
import org.devzendo.zarjaz.transceiver.NullTransceiver;
import org.devzendo.zarjaz.validation.ClientInterfaceValidator;
import org.devzendo.zarjaz.validation.ServerImplementationValidator;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;

/**
 * Copyright (C) 2008-2015 Matt Gumbley, DevZendo.org http://devzendo.org
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
public class TestTransports extends ConsoleLoggingUnittestCase {
    private static final Logger logger = LoggerFactory.getLogger(TestTransports.class);

    @Parameters
    public static Collection<Class> data() {
        return asList(NullTransport.class , TransceiverTransport.class);
    }

    private final String userName = "Matt";
    private final Class<? extends Transport> transportClass;
    private TimeoutScheduler timeoutScheduler;
    private Transport clientTransport;
    private Transport serverTransport;

    // runs before mocks initialised, so do real construction in @Before.
    public TestTransports(final Class<? extends Transport> transportClass) {
        this.transportClass = transportClass;
    }

    @Mock
    ClientInterfaceValidator clientValidator;

    @Mock
    ServerImplementationValidator serverValidator;

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws IllegalAccessException, InvocationTargetException, InstantiationException, NoSuchMethodException {
        timeoutScheduler = new TimeoutScheduler();
        if (transportClass.equals(NullTransport.class)) {
            clientTransport = new NullTransport(serverValidator, clientValidator, timeoutScheduler);
            serverTransport = clientTransport;
        } else if (transportClass.equals((TransceiverTransport.class))) {
            clientTransport = new TransceiverTransport(serverValidator, clientValidator, timeoutScheduler, new NullTransceiver(), new DefaultInvocationHashGenerator(), new DefaultInvocationCodec());
            serverTransport = clientTransport;
        }
    }

    @After
    public void tearDown() {
        if (clientTransport != null) {
            clientTransport.stop();
        }
        if (serverTransport != null) {
            serverTransport.stop();
        }
    }

    @Test
    public void multipleServerImplementationRegistrationsWithSameNameDisallowed() {
        final DefaultPrimeGenerator serverImplementation = new DefaultPrimeGenerator();

        thrown.expect(RegistrationException.class);
        thrown.expectMessage("The EndpointName 'primes' is already registered");

        serverTransport.registerServerImplementation(new EndpointName("primes"), PrimeGenerator.class, serverImplementation);
        serverTransport.registerServerImplementation(new EndpointName("primes"), PrimeGenerator.class, serverImplementation);
    }

    @Test
    public void registerServerImplementationValidates() {
        final DefaultTimeoutGenerator serverImplementation = new DefaultTimeoutGenerator();

        EndpointName timeoutEndpointName = new EndpointName("timeout");
        serverTransport.registerServerImplementation(timeoutEndpointName, TimeoutGenerator.class, serverImplementation);
        Mockito.verify(clientValidator).validateClientInterface(TimeoutGenerator.class);
        Mockito.verify(serverValidator).validateServerImplementation(TimeoutGenerator.class, serverImplementation);
    }

    @Test
    public void clientProxyValidates() {
        final DefaultTimeoutGenerator serverImplementation = new DefaultTimeoutGenerator();

        EndpointName timeoutEndpointName = new EndpointName("timeout");
        // for the null clientTransport, the impl has to be registered even tho only interested in the 'client' side.
        int invocationTimes = 1;
        if (clientTransport instanceof NullTransport) {
            clientTransport.registerServerImplementation(timeoutEndpointName, TimeoutGenerator.class, serverImplementation);
            invocationTimes++;
        }

        final TimeoutGenerator clientProxy = clientTransport.createClientProxy(timeoutEndpointName, TimeoutGenerator.class, 500L);
        Mockito.verify(clientValidator, Mockito.times(invocationTimes)).validateClientInterface(TimeoutGenerator.class);
    }

    @Test
    public void cannotStartWithNoClientBound() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("No clients or server implementations bound");

        clientTransport.start();
    }

    @Test
    public void cannotStartWithNoServerBound() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("No clients or server implementations bound");

        serverTransport.start();
    }

    @Test
    public void clientProxyRequestedForUnboundNameDisallowed() {
        // TODO
    }

    @Test
    public void cannotMakeClientCallsIfNotStarted() {
        // TODO
    }

    @Test
    public void cannotMakeClientCallsAfterStopped() {
        // TODO
    }
    // TODO but stopping the clientTransport will stop the timeoutscheduler - what if it is shared?
    // usage count in the scheduler would work...
    // Perhaps if you pass it in, it is used in a 'shared' mode, where it is not stopped when the clientTransport
    // is stopped - but if you construct the clientTransport without passing in the scheduler, it is stopped.

    @Test(timeout = 2000L)
    public void timeoutSchedulerExpectationsOnStartingAndStoppingTransport() {
        assertThat(timeoutScheduler.isStarted(), equalTo(false));

        // irrelevant to the test, but must have a client or server bound as it fails validation otherwise
        final EndpointName endpointName = new EndpointName("irrelevant");
        final DefaultTimeoutGenerator serverImplementation = new DefaultTimeoutGenerator();
        // for the null clientTransport, the impl has to be registered even tho only interested in the 'client' side.
        if (clientTransport instanceof NullTransport) {
            clientTransport.registerServerImplementation(endpointName, TimeoutGenerator.class, serverImplementation);
        }
        clientTransport.createClientProxy(endpointName, TimeoutGenerator.class, 500L);

        ThreadUtils.waitNoInterruption(250L);
        clientTransport.start();
        ThreadUtils.waitNoInterruption(250L);

        assertThat(timeoutScheduler.isStarted(), equalTo(true));

        ThreadUtils.waitNoInterruption(250L);
        clientTransport.stop();
        ThreadUtils.waitNoInterruption(250L);

        assertThat(timeoutScheduler.isStarted(), equalTo(false));
    }

    @Test(timeout = 2000L)
    public void roundTripWithoutTimeout() {
        final DefaultPrimeGenerator serverImplementation = new DefaultPrimeGenerator();

        EndpointName primesEndpointName = new EndpointName("primes");
        serverTransport.registerServerImplementation(primesEndpointName, PrimeGenerator.class, serverImplementation);
        Mockito.verify(clientValidator).validateClientInterface(PrimeGenerator.class);
        Mockito.verify(serverValidator).validateServerImplementation(PrimeGenerator.class, serverImplementation);

        final PrimeGenerator clientProxy = clientTransport.createClientProxy(primesEndpointName, PrimeGenerator.class, 500L);

        clientTransport.start();

        assertThat(clientProxy.generateNextPrimeMessage(userName), equalTo("Hello Matt, the next prime is 2"));
        assertThat(clientProxy.generateNextPrimeMessage(userName), equalTo("Hello Matt, the next prime is 3"));
    }

    @Test(timeout = 2000L)
    public void roundTripWithoutTimeoutAsynchronously() throws ExecutionException, InterruptedException {
        final DefaultPrimeGenerator serverImplementation = new DefaultPrimeGenerator();

        EndpointName primesEndpointName = new EndpointName("primes");
        serverTransport.registerServerImplementation(primesEndpointName, PrimeGenerator.class, serverImplementation);
        Mockito.verify(clientValidator).validateClientInterface(PrimeGenerator.class);
        Mockito.verify(serverValidator).validateServerImplementation(PrimeGenerator.class, serverImplementation);

        final PrimeGenerator clientProxy = clientTransport.createClientProxy(primesEndpointName, PrimeGenerator.class, 500L);

        clientTransport.start();

        final Future<String> first = clientProxy.generateNextPrimeMessageAsynchronously(userName);
        assertThat(first.get(), equalTo("Hello Matt, the next prime is 2"));
        final Future<String> second = clientProxy.generateNextPrimeMessageAsynchronously(userName);
        assertThat(second.get(), equalTo("Hello Matt, the next prime is 3"));
    }

    @Test(timeout = 4000L)
    public void timeoutOnClientSideThrowsAppropriateException() {
        serverTransport.registerServerImplementation(new EndpointName("timeout"), TimeoutGenerator.class, new DefaultTimeoutGenerator());

        final TimeoutGenerator clientProxy = clientTransport.createClientProxy(new EndpointName("timeout"), TimeoutGenerator.class, 500L);

        clientTransport.start();

        thrown.expect(MethodInvocationTimeoutException.class);
        thrown.expectMessage("Method call [timeout] 'sleepFor' timed out after 500ms");

        try {
            clientProxy.sleepFor(1000L);
        } finally {
            ThreadUtils.waitNoInterruption(2000L); // don't tear things down too early
        }
    }

    @Test(timeout = 3000L)
    public void timeoutOnClientSideTimesOutCorrectly() {
        serverTransport.registerServerImplementation(new EndpointName("timeout"), TimeoutGenerator.class, new DefaultTimeoutGenerator());

        final TimeoutGenerator clientProxy = clientTransport.createClientProxy(new EndpointName("timeout"), TimeoutGenerator.class, 500L);

        clientTransport.start();

        final long start = System.currentTimeMillis();
        try {
            logger.debug("calling sleepFor 1000L method on proxy " + clientProxy);
            clientProxy.sleepFor(1000L);
            // expecting an exception here!
        } catch (final MethodInvocationTimeoutException e) {
            final long stop = System.currentTimeMillis();
            long duration = stop - start;

            logger.info("Call with timeout round-trip was " + duration + " ms");
            assertThat(duration, greaterThanOrEqualTo(500L));
            assertThat(duration, lessThan(750L)); // if it takes more than 250ms over the timeout, we're doing something wrong
        } catch (final Exception e) {
            fail("Did not throw correct exception, got a " + e.getClass().getName());
        }

        ThreadUtils.waitNoInterruption(2000L); // don't tear things down too early
    }

    @Test(timeout = 3000L)
    public void timeoutsAreCancelledAfterNormalMethodCompletion() {
        // TODO
    }

    // TODO
    public void detectMethodExecutorPoolExhaustion() {
        // when all method executing threads in the pool are busy waiting for responses, need to detect the failure
        // to queue this new request
        // NullTransport-specific?
        // no, all transports will have a server-side executor thread pool.
        // need tests for metrics on the thread pool usage too.
    }


    private final EndpointName endpointName1 = new EndpointName("endpoint1");
    private final EndpointName endpointName2 = new EndpointName("endpoint2");
    private static final int METHOD_TIMEOUT_MILLISECONDS = 500;

    private interface SampleInterface {
        void someMethod();
    }

    private void registerSampleServerForNullTransport() {
        // need to register on server side first, in the NullTransport
        final SampleInterface server = new SampleInterface() {
            @Override
            public void someMethod() {
                // nothing
            }
        };
        serverTransport.registerServerImplementation(endpointName1, SampleInterface.class, server);
        serverTransport.registerServerImplementation(endpointName2, SampleInterface.class, server);
    }

    @Test
    public void cannotCreateClientProxyForSameEndpointNameTwice() {
        registerSampleServerForNullTransport();

        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Cannot register the same EndpointName/Client interface more than once");

        clientTransport.createClientProxy(endpointName1, SampleInterface.class, METHOD_TIMEOUT_MILLISECONDS);
        clientTransport.createClientProxy(endpointName1, SampleInterface.class, METHOD_TIMEOUT_MILLISECONDS);
    }

    @Test
    public void canCreateClientProxyTwiceForDifferentEndpointNames() {
        registerSampleServerForNullTransport();

        clientTransport.createClientProxy(endpointName1, SampleInterface.class, METHOD_TIMEOUT_MILLISECONDS);
        clientTransport.createClientProxy(endpointName2, SampleInterface.class, METHOD_TIMEOUT_MILLISECONDS);
    }
}
