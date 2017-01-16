package org.devzendo.zarjaz.protocol;

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
    public enum InitialFrameType {
        EXTENSION((byte) 0),
        METHOD_INVOCATION_HASHED((byte) 1),
        METHOD_INVOCATION_DECLARED((byte) 2),
        METHOD_RETURN_RESULT((byte) 3),
        METHOD_RETURN_EXCEPTION((byte) 4);

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
}
