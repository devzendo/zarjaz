package org.devzendo.zarjaz.protocol;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
public class Protocol {
    public static final int BUFFER_SIZE = 8192;
    public static final Charset UTF8 = Charset.forName("UTF-8");

    /**
     * The first byte of all protocol frames is an InitialFrameType.
     */
    public enum InitialFrameType {
        /**
         * Unused at present, if the initial frame type is EXTENSION, it denotes that the next two bytes hold the
         * full value of the frame type as a short.
         */
        EXTENSION((byte) 0),

        /**
         * METHOD_INVOCATION_HASHED indicates that the rest of the frame contains a MethodCallIdentifier, then a
         * MethodInvocationHash, followed by the argument list. Hashes are used to quickly refer to
         * [endpoint name/return type/method name/argument list classes].
         */
        METHOD_INVOCATION_HASHED((byte) 1),

        /**
         * METHOD_INVOCATION_DECLARED (unused at present) indicates that the rest of the frame contains a
         * MethodCallIdentifier, then a MethodInvocationDecalaration, followed by the argument list. The
         * MethodInvocationDeclaration is a dynamic mechanism for specifying the
         * [endpoint name/return type/method name/argument list classes] in the frame; it is a much larger, slower
         * method of invocation.
         */
        METHOD_INVOCATION_DECLARED((byte) 2),

        /**
         * This frame returns the successful result of a previous invocation (either by METHOD_INVOCATION_HASHED or
         * METHOD_INVOCATION_DECLARED). It is followed by a MethodCallIdentifier, then the encoded result.
         */
        METHOD_RETURN_RESULT((byte) 3),

        /**
         * This frame indicates a failure to invoke a previous invocation (either by METHOD_INVOCATION_HASHED or
         * METHOD_INVOCATION_DECLARED). It is followed by a MethodCallIdentifier, then the encoded exception as a
         * MethodReturnException.
         */
        METHOD_RETURN_EXCEPTION((byte) 4),

        /**
         * This frame is intentionally reserved for testing; it is not a valid frame.
         */
        TEST_INVALID_FRAME((byte) 255);

        /*
        some thoughts on why REST is popular, and how you use a health-checking/routing front end service like ARR....
        resources are addressable, then you can perform operations on them, although you're limited to CRUD that
        HTTP methods give you.
        Can achieve similar by passing resource addressing variables/ids into method calls, and update objects that
        might be serialised via JSON or somesuch.
        healthchecks would need a specific protocol exchange, each service impl could define a healthcheck method that
        is called to check availability, returning a boolean response? or some document type?

        an ARR-like system could map incoming endpoint requests to one or more actual servers, calling healthchecks,
        and routing accordingly. it'd have to maintain its own sequences, and translate requests/responses.
        how to translate URI-like things? they don't exist - but are resource addressing variables/ids/state.


        what about ping?

        what about listing the endpoints currently attached to this transport? or checking that a given endpoint exists?

        what about attaching a (possibly empty) set of String->String headers to each method invocation (e.g. for
        authentication)?

        what about rate limiting?
         */

        private static Map<Byte, InitialFrameType> reverseLookup = new HashMap<>();
        static {
            for (InitialFrameType ift : values()) {
                reverseLookup.put(ift.frameType, ift);
            }
        }
        public static Optional<InitialFrameType> decodeInitialFrameType(final byte b) {
            return Optional.ofNullable(reverseLookup.get(b));
        }

        private final byte frameType;
        InitialFrameType(final byte b)
        {
            this.frameType = b;
        }

        public byte getInitialFrameType() {
            return frameType;
        }
    }

    public class MethodCallIdentifier {

    }

    public class MethodInvocationHash {

    }

    public class MethodReturnException {

    }
}
