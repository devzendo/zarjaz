package org.devzendo.zarjaz.protocol;

import org.devzendo.zarjaz.reflect.DefaultInvocationHashGenerator;
import org.devzendo.zarjaz.reflect.InvocationHashGenerator;
import org.devzendo.zarjaz.transport.EndpointName;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasXPath;
import static org.hamcrest.Matchers.not;

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
public class TestInvocationCodec {
    private static final int SEQUENCE = 69;
    private static final Logger logger = LoggerFactory.getLogger(TestInvocationCodec.class);

    private static final byte[] fixedHash = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

    private final InvocationCodec codec = new DefaultInvocationCodec();
    private final EndpointName endpointName = new EndpointName("endpoint");
    private final EndpointName intentionallyMissingEndpoint = new EndpointName("IntentionallyMissing");
    private Method addOneMethod;
    private Method addOneWrapperMethod;
    private Method intentionallyNotRegisteredMethod;

    private interface SampleInterface {
        void firstMethod(int integer, boolean bool, String string);
    }

    private interface ComplexInterface {
        void firstMethod(int integer, boolean bool, String string);
        boolean secondMethod(String string);
    }

    private interface AddOnePrimitiveParameterInterface {
        public int addOne(int input);
    }

    private interface AddOneWrapperParameterInterface {
        public Integer addOne(Integer input);
    }

    private interface IntentionallyNotRegisteredInterface {
        void intentionallyNotRegisteredMethod();
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();


    @Before
    public void registerHashes() {
        final DefaultInvocationHashGenerator hashGenerator = new DefaultInvocationHashGenerator(endpointName);
        // it's valid (but dodgy) to have multiple interfaces registered under the same endpoint name - they will have
        // different hashes.
        codec.registerHashes(endpointName, AddOnePrimitiveParameterInterface.class, hashGenerator.generate(AddOnePrimitiveParameterInterface.class));
        codec.registerHashes(endpointName, AddOneWrapperParameterInterface.class, hashGenerator.generate(AddOneWrapperParameterInterface.class));

        addOneMethod = AddOnePrimitiveParameterInterface.class.getDeclaredMethods()[0];
        addOneWrapperMethod = AddOneWrapperParameterInterface.class.getDeclaredMethods()[0];
        intentionallyNotRegisteredMethod = IntentionallyNotRegisteredInterface.class.getDeclaredMethods()[0];
    }

    @Test
    public void detectsHashExistence() throws NoSuchMethodException {
        final InvocationHashGenerator gen = new DefaultInvocationHashGenerator(endpointName);
        final Map<Method, byte[]> methodMap = gen.generate(SampleInterface.class);

        assertThat(codec.registerHashes(endpointName, SampleInterface.class, methodMap), equalTo(Optional.empty()));

        final Optional<InvocationCodec.EndpointInterfaceMethod> secondRegistration = codec.registerHashes(endpointName, SampleInterface.class, methodMap);
        assertThat(secondRegistration, not(equalTo(Optional.empty())));
        final InvocationCodec.EndpointInterfaceMethod collision = secondRegistration.get();
        assertThat(collision.toString(), equalTo("Endpoint 'endpoint', Client interface 'SampleInterface', Method 'firstMethod'"));
    }

    @Test
    public void getMethodMap() throws NoSuchMethodException {
        final InvocationHashGenerator gen = new DefaultInvocationHashGenerator(endpointName);
        final Map<Method, byte[]> methodMap = gen.generate(ComplexInterface.class);

        codec.registerHashes(endpointName, ComplexInterface.class, methodMap);

        final Map<Method, byte[]> map = codec.getMethodsToHashMap(endpointName, ComplexInterface.class);
        assertThat(map.size(), equalTo(2));

        final Method firstMethod = ComplexInterface.class.getMethod("firstMethod", int.class, boolean.class, String.class);
        assertThat(map.containsKey(firstMethod), equalTo(true));
        assertThat(map.get(firstMethod), equalTo(methodMap.get(firstMethod)));

        final Method secondMethod = ComplexInterface.class.getMethod("secondMethod", String.class);
        assertThat(map.containsKey(secondMethod), equalTo(true));
        assertThat(map.get(secondMethod), equalTo(methodMap.get(secondMethod)));
    }

    @Test
    public void encodingOfMethodCall() throws NoSuchMethodException {
        final InvocationHashGenerator gen = new DefaultInvocationHashGenerator(endpointName);
        final Map<Method, byte[]> methodMap = gen.generate(SampleInterface.class);
        final Method firstMethod = SampleInterface.class.getMethod("firstMethod", int.class, boolean.class, String.class);
        final byte[] hash = methodMap.get(firstMethod);
        assertThat(hash.length, equalTo(16));

        codec.registerHashes(endpointName, SampleInterface.class, methodMap);
        final List<ByteBuffer> byteBuffers = codec.generateHashedMethodInvocation(0, endpointName, SampleInterface.class, firstMethod, new Object[]{ 201, true, "boo" });
        assertThat(byteBuffers.size(), equalTo(1));
        final ByteBuffer buffer = byteBuffers.get(0);
        assertThat(buffer.limit(), equalTo(33));
        final byte[] frame = Arrays.copyOf(buffer.array(), buffer.limit());

        assertThat(frame, equalTo(new byte[] {
                Protocol.InitialFrameType.METHOD_INVOCATION_HASHED.getInitialFrameType(),

                // sequence
                0,
                0,
                0,
                0,

                // hash
                40,
                -75,
                34,
                109,
                -49,
                -65,
                -113,
                -35,
                11,
                117,
                91,
                79,
                126,
                20,
                -110,
                -90,

                // 201
                0,
                0,
                0,
                (byte) 0xc9,

                // true
                1,

                // "boo"
                0,
                0,
                0,
                3, // length
                'b',
                'o',
                'o'
        }));
    }

    @Test
    public void receiveMethodInvocationIncompatibleArgumentTypes() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The parameter value type 'String' cannot be converted to the parameter type 'int'");

        codec.generateHashedMethodInvocation(SEQUENCE, endpointName, AddOnePrimitiveParameterInterface.class, addOneMethod, new Object[] { "boom" });
    }

    @Test
    public void receiveMethodInvocationArgumentConversionCannotBeAssignedBecauseTooWide() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The parameter value type 'Long' cannot be converted to the parameter type 'int'");

        codec.generateHashedMethodInvocation(SEQUENCE, endpointName, AddOnePrimitiveParameterInterface.class, addOneMethod, new Object[] { (long) 69L });
    }

    @Test
    public void receiveMethodInvocationArgumentConversionCanBeAssignedButWidened() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The parameter value type 'Short' cannot be converted to the parameter type 'int'"); // TODO yet

        codec.generateHashedMethodInvocation(SEQUENCE, endpointName, AddOnePrimitiveParameterInterface.class, addOneMethod, new Object[] { (short) 69 });
    }

    @Test
    public void receiveMethodInvocationIncompatibleArgumentTypesWrapperInterface() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The parameter value type 'String' cannot be converted to the parameter type 'Integer'");

        codec.generateHashedMethodInvocation(SEQUENCE, endpointName, AddOneWrapperParameterInterface.class, addOneWrapperMethod, new Object[] { "boom" });
    }

    @Test
    public void receiveMethodInvocationArgumentConversionCannotBeAssignedBecauseTooWideWrapperInterface() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The parameter value type 'Long' cannot be converted to the parameter type 'Integer'");

        codec.generateHashedMethodInvocation(SEQUENCE, endpointName, AddOneWrapperParameterInterface.class, addOneWrapperMethod, new Object[] { (long) 69L });
    }

    @Test
    public void receiveMethodInvocationArgumentConversionCanBeAssignedButWidenedWrapperInterface() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The parameter value type 'Short' cannot be converted to the parameter type 'Integer'"); // TODO yet

        codec.generateHashedMethodInvocation(SEQUENCE, endpointName, AddOneWrapperParameterInterface.class, addOneWrapperMethod, new Object[] { (short) 69 });
    }

    @Test
    public void receiveMethodInvocationEndpointNotFound() {
        expectLookupFailure();

        codec.generateHashedMethodInvocation(SEQUENCE, intentionallyMissingEndpoint, AddOnePrimitiveParameterInterface.class, addOneMethod, new Object[0]);
    }

    @Test
    public void receiveMethodInvocationClientInterfaceNotFound() {
        expectLookupFailure();

        codec.generateHashedMethodInvocation(SEQUENCE, endpointName, IntentionallyNotRegisteredInterface.class, addOneMethod, new Object[0]);
    }

    @Test
    public void receiveMethodInvocationMethodNotFound() {
        expectLookupFailure();

        codec.generateHashedMethodInvocation(SEQUENCE, endpointName, AddOnePrimitiveParameterInterface.class, intentionallyNotRegisteredMethod, new Object[0]);
    }

    @Test
    public void receiveMethodInvocationEndpointAndClientInterfaceNotFound() {
        expectLookupFailure();

        codec.generateHashedMethodInvocation(SEQUENCE, intentionallyMissingEndpoint, IntentionallyNotRegisteredInterface.class, addOneMethod, new Object[0]);
    }

    @Test
    public void receiveMethodInvocationClientInterfaceAndMethodNotFound() {
        expectLookupFailure();

        codec.generateHashedMethodInvocation(SEQUENCE, endpointName, IntentionallyNotRegisteredInterface.class, intentionallyNotRegisteredMethod, new Object[0]);
    }

    @Test
    public void receiveMethodInvocationEndpointAndMethodNotFound() {
        expectLookupFailure();

        codec.generateHashedMethodInvocation(SEQUENCE, endpointName, AddOnePrimitiveParameterInterface.class, intentionallyNotRegisteredMethod, new Object[0]);
    }

    private void expectLookupFailure() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Hash lookup of endpoint name / client interface / method failed");
    }
}
