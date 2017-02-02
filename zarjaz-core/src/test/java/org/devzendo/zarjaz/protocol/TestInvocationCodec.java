package org.devzendo.zarjaz.protocol;

import org.devzendo.commoncode.string.HexDump;
import org.devzendo.zarjaz.reflect.DefaultInvocationHashGenerator;
import org.devzendo.zarjaz.reflect.InvocationHashGenerator;
import org.devzendo.zarjaz.transport.EndpointName;
import org.junit.Assert;
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
    private static final Logger logger = LoggerFactory.getLogger(TestInvocationCodec.class);

    private static final byte[] fixedHash = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

    private final InvocationCodec codec = new DefaultInvocationCodec();
    private final EndpointName endpointName = new EndpointName("endpoint");

    private interface SampleInterface {
        void firstMethod(int integer, boolean bool, String string);
    }

    private interface ComplexInterface {
        void firstMethod(int integer, boolean bool, String string);
        boolean secondMethod(String string);
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();


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
    public void testEncodingOfMethodCall() throws NoSuchMethodException {
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
}
