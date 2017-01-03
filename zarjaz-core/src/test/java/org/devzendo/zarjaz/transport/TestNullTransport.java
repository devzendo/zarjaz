package org.devzendo.zarjaz.transport;

import org.apache.log4j.BasicConfigurator;
import org.devzendo.commoncode.concurrency.ThreadUtils;
import org.devzendo.zarjaz.sample.primes.DefaultPrimeGenerator;
import org.devzendo.zarjaz.sample.primes.PrimeGenerator;
import org.devzendo.zarjaz.sample.timeout.DefaultTimeoutGenerator;
import org.devzendo.zarjaz.sample.timeout.TimeoutGenerator;
import org.devzendo.zarjaz.timeout.TimeoutScheduler;
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

import java.lang.reflect.Constructor;
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
public class TestNullTransport {
    private static final Logger logger = LoggerFactory.getLogger(TestNullTransport.class);

    {
        BasicConfigurator.configure();
    }

    @Parameters
    public static Collection<Class> data() {
        return asList(NullTransport.class);
    }

    private final String userName = "Matt";
    private final Class<? extends Transport> transportClass;
    private TimeoutScheduler timeoutScheduler;
    private Transport transport;

    // runs before mocks initialised, so do real construction in @Before.
    public TestNullTransport(final Class<? extends Transport> transportClass) {
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
        final Constructor<? extends Transport> constructor = transportClass.getConstructor(ServerImplementationValidator.class, ClientInterfaceValidator.class, TimeoutScheduler.class);
        transport = constructor.newInstance(serverValidator, clientValidator, timeoutScheduler);
    }

    @After
    public void tearDown() {
        if (transport != null) {
            transport.stop();
        }
    }

    @Test
    public void multipleServerImplementationRegistrationsWithSameNameDisallowed() {
        final DefaultPrimeGenerator serverImplementation = new DefaultPrimeGenerator();

        thrown.expect(RegistrationException.class);
        thrown.expectMessage("The EndpointName 'primes' is already registered");

        transport.registerServerImplementation(new EndpointName("primes"), PrimeGenerator.class, serverImplementation);
        transport.registerServerImplementation(new EndpointName("primes"), PrimeGenerator.class, serverImplementation);
    }

    @Test
    public void registerServerImplementationValidates() {
        final DefaultTimeoutGenerator serverImplementation = new DefaultTimeoutGenerator();

        EndpointName timeoutEndpointName = new EndpointName("timeout");
        transport.registerServerImplementation(timeoutEndpointName, TimeoutGenerator.class, serverImplementation);
        Mockito.verify(clientValidator).validateClientInterface(TimeoutGenerator.class);
        Mockito.verify(serverValidator).validateServerImplementation(TimeoutGenerator.class, serverImplementation);
    }

    @Test
    public void clientProxyValidates() {
        final DefaultTimeoutGenerator serverImplementation = new DefaultTimeoutGenerator();

        EndpointName timeoutEndpointName = new EndpointName("timeout");
        // for the null transport, the impl has to be registered even tho only interested in the 'client' side.
        transport.registerServerImplementation(timeoutEndpointName, TimeoutGenerator.class, serverImplementation);

        final TimeoutGenerator clientProxy = transport.createClientProxy(timeoutEndpointName, TimeoutGenerator.class, 500L);
        Mockito.verify(clientValidator, Mockito.times(2)).validateClientInterface(TimeoutGenerator.class);
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
    // TODO but stopping the transport will stop the timeoutscheduler - what if it is shared?
    // usage count in the scheduler would work...
    // Perhaps if you pass it in, it is used in a 'shared' mode, where it is not stopped when the transport
    // is stopped - but if you construct the transport without passing in the scheduler, it is stopped.

    @Test(timeout = 2000L)
    public void timeoutSchedulerExpectationsOnStartingAndStoppingTransport() {
        assertThat(timeoutScheduler.isStarted(), equalTo(false));

        ThreadUtils.waitNoInterruption(250L);
        transport.start();
        ThreadUtils.waitNoInterruption(250L);

        assertThat(timeoutScheduler.isStarted(), equalTo(true));

        ThreadUtils.waitNoInterruption(250L);
        transport.stop();
        ThreadUtils.waitNoInterruption(250L);

        assertThat(timeoutScheduler.isStarted(), equalTo(false));
    }

    @Test(timeout = 2000L)
    public void roundTripWithoutTimeout() {
        final DefaultPrimeGenerator serverImplementation = new DefaultPrimeGenerator();

        EndpointName primesEndpointName = new EndpointName("primes");
        transport.registerServerImplementation(primesEndpointName, PrimeGenerator.class, serverImplementation);
        Mockito.verify(clientValidator).validateClientInterface(PrimeGenerator.class);
        Mockito.verify(serverValidator).validateServerImplementation(PrimeGenerator.class, serverImplementation);

        final PrimeGenerator clientProxy = transport.createClientProxy(primesEndpointName, PrimeGenerator.class, 500L);

        transport.start();

        assertThat(clientProxy.generateNextPrimeMessage(userName), equalTo("Hello Matt, the next prime is 2"));
        assertThat(clientProxy.generateNextPrimeMessage(userName), equalTo("Hello Matt, the next prime is 3"));
    }

    @Test(timeout = 2000L)
    public void roundTripWithoutTimeoutAsynchronously() throws ExecutionException, InterruptedException {
        final DefaultPrimeGenerator serverImplementation = new DefaultPrimeGenerator();

        EndpointName primesEndpointName = new EndpointName("primes");
        transport.registerServerImplementation(primesEndpointName, PrimeGenerator.class, serverImplementation);
        Mockito.verify(clientValidator).validateClientInterface(PrimeGenerator.class);
        Mockito.verify(serverValidator).validateServerImplementation(PrimeGenerator.class, serverImplementation);

        final PrimeGenerator clientProxy = transport.createClientProxy(primesEndpointName, PrimeGenerator.class, 500L);

        transport.start();

        final Future<String> first = clientProxy.generateNextPrimeMessageAsynchronously(userName);
        assertThat(first.get(), equalTo("Hello Matt, the next prime is 2"));
        final Future<String> second = clientProxy.generateNextPrimeMessageAsynchronously(userName);
        assertThat(second.get(), equalTo("Hello Matt, the next prime is 3"));
    }

    @Test(timeout = 4000L)
    public void timeoutOnClientSideThrowsAppropriateException() {

        transport.registerServerImplementation(new EndpointName("timeout"), TimeoutGenerator.class, new DefaultTimeoutGenerator());

        final TimeoutGenerator clientProxy = transport.createClientProxy(new EndpointName("timeout"), TimeoutGenerator.class, 500L);

        transport.start();

        thrown.expect(MethodInvocationTimeoutException.class);
        thrown.expectMessage("method call [timeout] 'sleepFor' timed out after 500ms");

        try {
            clientProxy.sleepFor(1000L);
        } finally {
            ThreadUtils.waitNoInterruption(2000L); // don't tear things down too early
        }
    }

    @Test(timeout = 3000L)
    public void timeoutOnClientSideTimesOutCorrectly() {
        transport.registerServerImplementation(new EndpointName("timeout"), TimeoutGenerator.class, new DefaultTimeoutGenerator());

        final TimeoutGenerator clientProxy = transport.createClientProxy(new EndpointName("timeout"), TimeoutGenerator.class, 500L);

        transport.start();

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
}
