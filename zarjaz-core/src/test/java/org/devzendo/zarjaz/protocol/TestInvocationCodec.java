package org.devzendo.zarjaz.protocol;

import org.apache.log4j.BasicConfigurator;
import org.devzendo.commoncode.string.HexDump;
import org.devzendo.zarjaz.nio.DefaultWritableByteBuffer;
import org.devzendo.zarjaz.nio.ReadableByteBuffer;
import org.devzendo.zarjaz.nio.WritableByteBuffer;
import org.devzendo.zarjaz.reflect.DefaultInvocationHashGenerator;
import org.devzendo.zarjaz.reflect.InvocationHashGenerator;
import org.devzendo.zarjaz.transport.EndpointName;
import org.devzendo.zarjaz.util.BufferDumper;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.asList;
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
public class TestInvocationCodec {
    private static final int SEQUENCE = 69;
    private static final Logger logger = LoggerFactory.getLogger(TestInvocationCodec.class);

    {
        BasicConfigurator.configure();
    }

    private static final byte[] fixedHash = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

    private final InvocationCodec codec = new DefaultInvocationCodec();
    private final EndpointName endpointName = new EndpointName("endpoint");
    private final EndpointName intentionallyMissingEndpoint = new EndpointName("IntentionallyMissing");
    private final Method addOneMethod = AddOnePrimitiveParameterInterface.class.getDeclaredMethods()[0];
    private final Method addOneWrapperMethod = AddOneWrapperParameterInterface.class.getDeclaredMethods()[0];
    private final Method intentionallyNotRegisteredMethod = IntentionallyNotRegisteredInterface.class.getDeclaredMethods()[0];
    private final Method firstMethod = SampleInterface.class.getDeclaredMethods()[0];
    private final Method noArgsMethod = NoArgsMethodInterface.class.getDeclaredMethods()[0];

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

    private interface NoArgsMethodInterface {
        void noArgsMethod();
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    // -----------------------------------------------------------------------------------------------------------------
    // Hash collision tests.
    //
    // Store this hash under some other endpoint/interface/method, so when we register SampleInterface, we get a hash collision.
    // If we stored it under the same endpoint/interface/method, the collision check would not trigger, as multiple registrations of
    // the same details are allowed (pointless, but not in the case of the NullTransceiver/TransceiverTransport round trip
    // test, where the same transceiver has both client and servers registered - both these halves need to register hashes,
    // and if the check wasn't made for the different endpoint/interface/method, then these two registrations of
    // the same information would collide.

    @Test
    public void detectsHashCollisionDifferentInterfaceClass() throws NoSuchMethodException {
        final InvocationHashGenerator gen = new DefaultInvocationHashGenerator();
        final Map<Method, byte[]> methodMap = gen.generate(endpointName, SampleInterface.class);
        final Method method = SampleInterface.class.getDeclaredMethods()[0];
        final byte[] hash = methodMap.get(method);

        final Class<NoArgsMethodInterface> differentInterfaceClass = NoArgsMethodInterface.class;
        ((DefaultInvocationCodec)codec)._createHashCollision(endpointName, differentInterfaceClass, hash, method);

        final Optional<InvocationCodec.EndpointInterfaceMethod> secondRegistration = codec.registerHashes(endpointName, SampleInterface.class, methodMap);
        assertThat(secondRegistration, not(equalTo(Optional.empty())));
        final InvocationCodec.EndpointInterfaceMethod collision = secondRegistration.get();
        assertThat(collision.toString(), equalTo("Endpoint 'endpoint', Client interface 'NoArgsMethodInterface', Method 'firstMethod'"));
    }

    // TODO flapping test
    @Test
    public void detectsHashCollisionDifferentMethod() throws NoSuchMethodException {
        final InvocationHashGenerator gen = new DefaultInvocationHashGenerator();
        final Map<Method, byte[]> methodMap = gen.generate(endpointName, ComplexInterface.class);
        final Method firstMethod = ComplexInterface.class.getDeclaredMethods()[0];
        final Method secondMethod = ComplexInterface.class.getDeclaredMethods()[1];
        final byte[] hash = methodMap.get(firstMethod);

        final Method differentMethod = secondMethod;
        ((DefaultInvocationCodec)codec)._createHashCollision(endpointName, ComplexInterface.class, hash, differentMethod);

        final Optional<InvocationCodec.EndpointInterfaceMethod> secondRegistration = codec.registerHashes(endpointName, ComplexInterface.class, methodMap);
        assertThat(secondRegistration, not(equalTo(Optional.empty())));
        final InvocationCodec.EndpointInterfaceMethod collision = secondRegistration.get();
        assertThat(collision.toString(), equalTo("Endpoint 'endpoint', Client interface 'ComplexInterface', Method 'secondMethod'"));
    }

    @Test
    public void detectsHashCollisionDifferentEndpointName() throws NoSuchMethodException {
        final InvocationHashGenerator gen = new DefaultInvocationHashGenerator();
        final Map<Method, byte[]> methodMap = gen.generate(endpointName, SampleInterface.class);
        final Method method = SampleInterface.class.getDeclaredMethods()[0];
        final byte[] hash = methodMap.get(method);

        final EndpointName differentEndpointName = new EndpointName("slartibartfast");
        ((DefaultInvocationCodec)codec)._createHashCollision(differentEndpointName, SampleInterface.class, hash, method);

        final Optional<InvocationCodec.EndpointInterfaceMethod> secondRegistration = codec.registerHashes(endpointName, SampleInterface.class, methodMap);
        assertThat(secondRegistration, not(equalTo(Optional.empty())));
        final InvocationCodec.EndpointInterfaceMethod collision = secondRegistration.get();
        assertThat(collision.toString(), equalTo("Endpoint 'slartibartfast', Client interface 'SampleInterface', Method 'firstMethod'"));
    }

    @Test
    public void reRegistrationOfSameDoesNotCauseHashCollision() throws NoSuchMethodException {
        final InvocationHashGenerator gen = new DefaultInvocationHashGenerator();
        final Map<Method, byte[]> methodMap = gen.generate(endpointName, SampleInterface.class);

        // first registration
        assertThat(codec.registerHashes(endpointName, SampleInterface.class, methodMap), equalTo(Optional.empty()));

        // second registration
        assertThat(codec.registerHashes(endpointName, SampleInterface.class, methodMap), equalTo(Optional.empty()));
    }

    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void getMethodMap() throws NoSuchMethodException {
        final InvocationHashGenerator gen = new DefaultInvocationHashGenerator();
        final Map<Method, byte[]> methodMap = gen.generate(endpointName, ComplexInterface.class);

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
    public void encodingOfHashedMethodInvocation() throws NoSuchMethodException {
        final InvocationHashGenerator gen = new DefaultInvocationHashGenerator();
        final Map<Method, byte[]> methodMap = gen.generate(endpointName, SampleInterface.class);
        final Method firstMethod = SampleInterface.class.getMethod("firstMethod", int.class, boolean.class, String.class);
        final byte[] hash = methodMap.get(firstMethod);
        assertThat(hash.length, equalTo(16));

        codec.registerHashes(endpointName, SampleInterface.class, methodMap);
        final List<ReadableByteBuffer> byteBuffers = codec.generateHashedMethodInvocation(0, endpointName, SampleInterface.class, firstMethod, new Object[]{ 201, true, "boo" });
        assertThat(byteBuffers.size(), equalTo(1));
        final ReadableByteBuffer buffer = byteBuffers.get(0);
        assertThat(buffer.limit(), equalTo(33));
        final byte[] frame = Arrays.copyOf(buffer.raw().array(), buffer.limit());

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
        registerAddOneHashes();

        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The parameter value type 'String' cannot be converted to the parameter type 'int'");

        codec.generateHashedMethodInvocation(SEQUENCE, endpointName, AddOnePrimitiveParameterInterface.class, addOneMethod, new Object[] { "boom" });
    }

    @Test
    public void receiveMethodInvocationArgumentConversionCannotBeAssignedBecauseTooWide() {
        registerAddOneHashes();

        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The parameter value type 'Long' cannot be converted to the parameter type 'int'");

        codec.generateHashedMethodInvocation(SEQUENCE, endpointName, AddOnePrimitiveParameterInterface.class, addOneMethod, new Object[] { (long) 69L });
    }

    @Test
    public void receiveMethodInvocationArgumentConversionCanBeAssignedButWidened() {
        registerAddOneHashes();

        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The parameter value type 'Short' cannot be converted to the parameter type 'int'"); // TODO yet

        codec.generateHashedMethodInvocation(SEQUENCE, endpointName, AddOnePrimitiveParameterInterface.class, addOneMethod, new Object[] { (short) 69 });
    }

    @Test
    public void receiveMethodInvocationIncompatibleArgumentTypesWrapperInterface() {
        registerAddOneHashes();

        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The parameter value type 'String' cannot be converted to the parameter type 'Integer'");

        codec.generateHashedMethodInvocation(SEQUENCE, endpointName, AddOneWrapperParameterInterface.class, addOneWrapperMethod, new Object[] { "boom" });
    }

    @Test
    public void receiveMethodInvocationArgumentConversionCannotBeAssignedBecauseTooWideWrapperInterface() {
        registerAddOneHashes();

        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The parameter value type 'Long' cannot be converted to the parameter type 'Integer'");

        codec.generateHashedMethodInvocation(SEQUENCE, endpointName, AddOneWrapperParameterInterface.class, addOneWrapperMethod, new Object[] { (long) 69L });
    }

    @Test
    public void receiveMethodInvocationArgumentConversionCanBeAssignedButWidenedWrapperInterface() {
        registerAddOneHashes();

        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The parameter value type 'Short' cannot be converted to the parameter type 'Integer'"); // TODO yet

        codec.generateHashedMethodInvocation(SEQUENCE, endpointName, AddOneWrapperParameterInterface.class, addOneWrapperMethod, new Object[] { (short) 69 });
    }

    @Test
    public void receiveMethodInvocationEndpointNotFound() {
        registerAddOneHashes();

        expectLookupFailure();

        codec.generateHashedMethodInvocation(SEQUENCE, intentionallyMissingEndpoint, AddOnePrimitiveParameterInterface.class, addOneMethod, new Object[0]);
    }

    @Test
    public void receiveMethodInvocationClientInterfaceNotFound() {
        registerAddOneHashes();

        expectLookupFailure();

        codec.generateHashedMethodInvocation(SEQUENCE, endpointName, IntentionallyNotRegisteredInterface.class, addOneMethod, new Object[0]);
    }

    @Test
    public void receiveMethodInvocationMethodNotFound() {
        registerAddOneHashes();

        expectLookupFailure();

        codec.generateHashedMethodInvocation(SEQUENCE, endpointName, AddOnePrimitiveParameterInterface.class, intentionallyNotRegisteredMethod, new Object[0]);
    }

    @Test
    public void receiveMethodInvocationEndpointAndClientInterfaceNotFound() {
        registerAddOneHashes();

        expectLookupFailure();

        codec.generateHashedMethodInvocation(SEQUENCE, intentionallyMissingEndpoint, IntentionallyNotRegisteredInterface.class, addOneMethod, new Object[0]);
    }

    @Test
    public void receiveMethodInvocationClientInterfaceAndMethodNotFound() {
        registerAddOneHashes();

        expectLookupFailure();

        codec.generateHashedMethodInvocation(SEQUENCE, endpointName, IntentionallyNotRegisteredInterface.class, intentionallyNotRegisteredMethod, new Object[0]);
    }

    @Test
    public void receiveMethodInvocationEndpointAndMethodNotFound() {
        registerAddOneHashes();

        expectLookupFailure();

        codec.generateHashedMethodInvocation(SEQUENCE, endpointName, AddOnePrimitiveParameterInterface.class, intentionallyNotRegisteredMethod, new Object[0]);
    }

    @Test
    public void decodeInvalidFrame() throws NoSuchMethodException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeByte(Protocol.InitialFrameType.TEST_INVALID_FRAME.getInitialFrameType());
        final List<ReadableByteBuffer> buffers = encoder.getBuffers();

        final Optional<InvocationCodec.DecodedFrame> decodedFrame = codec.decodeFrame(buffers);
        assertThat(decodedFrame.isPresent(), is(false));
    }

    @Test
    public void decodeHashedMethodInvocationNoSuchHashExists() throws NoSuchMethodException {
        final List<ReadableByteBuffer> byteBuffers = generateSampleHashedMethodInvocation();
        // munge the hash
        final byte[] array = Arrays.copyOf(byteBuffers.get(0).raw().array(), byteBuffers.get(0).limit());
        for (int i = 5; i < array.length; i++) {
            array[i]++;
        }
        final WritableByteBuffer buffer = DefaultWritableByteBuffer.allocate(array.length);
        buffer.put(Arrays.copyOf(array, array.length));
        final ReadableByteBuffer readableByteBuffer = buffer.flip();

        assert(!codec.decodeFrame(asList(readableByteBuffer)).isPresent());
        // TODO METRIC hash lookup failure increment
        // which would be the way to detect this specific parse failure
    }

    @Test
    public void decodeHashedMethodInvocationOfNoArgsMethod() throws NoSuchMethodException {
        final List<ReadableByteBuffer> byteBuffers = generateNoArgsMethodHashedMethodInvocation();
        BufferDumper.dumpBuffers(byteBuffers);

        final Optional<InvocationCodec.DecodedFrame> decodedFrame = codec.decodeFrame(byteBuffers);
        assert(decodedFrame.isPresent());

        final InvocationCodec.HashedMethodInvocation frame = (InvocationCodec.HashedMethodInvocation) decodedFrame.get();
        assertThat(frame.sequence, equalTo(42));

        assertThat(frame.endpointInterfaceMethod.getEndpointName(), equalTo(endpointName));
        assertThat(frame.endpointInterfaceMethod.getClientInterface(), equalTo(NoArgsMethodInterface.class));
        assertThat(frame.endpointInterfaceMethod.getMethod(), equalTo(noArgsMethod));

        assertThat(frame.args, not(nullValue()));
        assertThat(frame.args.length, equalTo(0));
    }

    @Test
    public void decodeHashedMethodInvocation() throws NoSuchMethodException {
        final List<ReadableByteBuffer> byteBuffers = generateSampleHashedMethodInvocation();
        BufferDumper.dumpBuffers(byteBuffers);

        final Optional<InvocationCodec.DecodedFrame> decodedFrame = codec.decodeFrame(byteBuffers);
        assert(decodedFrame.isPresent());

        final InvocationCodec.HashedMethodInvocation frame = (InvocationCodec.HashedMethodInvocation) decodedFrame.get();
        assertThat(frame.sequence, equalTo(42));

        assertThat(frame.endpointInterfaceMethod.getEndpointName(), equalTo(endpointName));
        assertThat(frame.endpointInterfaceMethod.getClientInterface(), equalTo(SampleInterface.class));
        assertThat(frame.endpointInterfaceMethod.getMethod(), equalTo(firstMethod));

        assertThat(frame.args, not(nullValue()));
        assertThat(frame.args.length, equalTo(3));
        assertThat(frame.args[0], Matchers.instanceOf(Integer.TYPE)); // note, primitive, as in interface/method
        assertThat(frame.args[0], equalTo(201));
        assertThat(frame.args[1], Matchers.instanceOf(Boolean.TYPE)); // note, primitive, as in interface/method
        assertThat(frame.args[1], equalTo(true));
        assertThat(frame.args[2], Matchers.instanceOf(String.class));
        assertThat(frame.args[2], equalTo("boo"));
    }

    @Test
    public void decodeHashedMethodInvocationOfTruncatedFrames() throws NoSuchMethodException {
        final List<ReadableByteBuffer> byteBuffers = generateNoArgsMethodHashedMethodInvocation();
        assert(codec.decodeFrame(byteBuffers).isPresent()); // the good case

        // now truncate this repeatedly until empty, observer decode failure
        final byte[] array = Arrays.copyOf(byteBuffers.get(0).raw().array(), byteBuffers.get(0).limit());
        for (int truncatedLength = array.length - 1; truncatedLength >= 0; truncatedLength--) {
            final WritableByteBuffer buffer = DefaultWritableByteBuffer.allocate(truncatedLength);
            buffer.put(Arrays.copyOf(array, truncatedLength));
            final ReadableByteBuffer readableByteBuffer = buffer.flip();
            logger.debug("length is " + truncatedLength);
            assertThat(codec.decodeFrame(asList(readableByteBuffer)).isPresent(), equalTo(false));
        }
    }

    @Test
    public void encodingOfMethodReturnResponse() {
        final List<ReadableByteBuffer> byteBuffers = codec.generateMethodReturnResponse(201, String.class, "endofunctor");
        BufferDumper.dumpBuffers(byteBuffers);
        assertThat(byteBuffers.size(), equalTo(1));
        final ReadableByteBuffer buffer = byteBuffers.get(0);
        assertThat(buffer.limit(), equalTo(20));
        final byte[] frame = Arrays.copyOf(buffer.raw().array(), buffer.limit());

        assertThat(frame, equalTo(new byte[] {
                Protocol.InitialFrameType.METHOD_RETURN_RESULT.getInitialFrameType(),

                // sequence
                0,
                0,
                0,
                (byte) 201,

                // String length
                0,
                0,
                0,
                11,
                'e',
                'n',
                'd',
                'o',
                'f',
                'u',
                'n',
                'c',
                't',
                'o',
                'r'
        }));
    }

    private void registerAddOneHashes() {
        final DefaultInvocationHashGenerator hashGenerator = new DefaultInvocationHashGenerator();
        // it's valid (but dodgy) to have multiple interfaces registered under the same endpoint name - they will have
        // different hashes.
        codec.registerHashes(endpointName, AddOnePrimitiveParameterInterface.class, hashGenerator.generate(endpointName, AddOnePrimitiveParameterInterface.class));
        codec.registerHashes(endpointName, AddOneWrapperParameterInterface.class, hashGenerator.generate(endpointName, AddOneWrapperParameterInterface.class));
    }

    private void expectLookupFailure() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Hash lookup of endpoint name / client interface / method failed");
    }

    private List<ReadableByteBuffer> generateNoArgsMethodHashedMethodInvocation() {
        return generateHashedMethodInvocation(NoArgsMethodInterface.class, new Object[0]);
    }

    private List<ReadableByteBuffer> generateSampleHashedMethodInvocation() {
        return generateHashedMethodInvocation(SampleInterface.class, new Object[]{201, true, "boo"});
    }

    private List<ReadableByteBuffer> generateHashedMethodInvocation(final Class<?> interfaceClass, final Object[] args) {
        final InvocationHashGenerator gen = new DefaultInvocationHashGenerator();
        final Map<Method, byte[]> methodMap = gen.generate(endpointName, interfaceClass);
        final Method method = interfaceClass.getDeclaredMethods()[0];
        final byte[] hash = methodMap.get(method);
        logger.debug("hash of method is: " + HexDump.bytes2hex(hash));
        codec.registerHashes(endpointName, interfaceClass, methodMap);
        return codec.generateHashedMethodInvocation(42, endpointName, interfaceClass, method, args);
    }
}
