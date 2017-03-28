package org.devzendo.zarjaz.util;

import org.devzendo.commoncode.string.HexDump;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;

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
public class BufferDumper {
    private static final Logger logger = LoggerFactory.getLogger(BufferDumper.class);

    public static void dumpBuffer(final String caption, final ByteBuffer buffer) {
        logger.debug(" --- " + caption + " bytebuffer of position " + buffer.position() + " limit " + buffer.limit() + " remaining " + buffer.remaining() + " ---");
        final byte[] array = new byte[buffer.remaining()];
        for (int i = buffer.position(), j = 0; i < buffer.limit(); i++, j++) {
            array[j] = buffer.get(i);
        }
        final String[] strings = HexDump.hexDump(array);
        for (String string: strings) {
            logger.debug(string);
        }
        logger.debug(" ---");
    }

    public static void equalData(final String thisCaption, final ByteBuffer thisbuf, final String thatCaption, final ByteBuffer thatbuf) {
        logger.debug(" === " + thisCaption + " bytebuffer of position " + thisbuf.position() + " limit " + thisbuf.limit() + " remaining " + thisbuf.remaining() + " ===");
        logger.debug(" === " + thatCaption + " bytebuffer of position " + thatbuf.position() + " limit " + thatbuf.limit() + " remaining " + thatbuf.remaining() + " ===");
        int p = thisbuf.position();
        for (int i = thisbuf.position(), j = thatbuf.position(); i < thisbuf.limit(); i++, j++) {
            final byte thisbyte = thisbuf.get(i);
            final byte thatbyte = thatbuf.get(j);
            logger.debug("#" + i + " " + thisCaption + "=0x" + HexDump.byte2hex(thisbyte) + " " + (thisbyte == thatbyte ? "=" : "|") + " " + thatCaption + "=0x" + HexDump.byte2hex(thatbyte));
            if (thisbyte != thatbyte) {
                logger.debug("data not equal; false");
                return;
            }
        }
        logger.debug("data equal; true");
    }

    public static void dumpBuffers(final List<ByteBuffer> buffers) {
        logger.debug(" --- There are " + buffers.size() + " buffer(s) ---");
        int count = 0;
        for (ByteBuffer buffer: buffers) {
            dumpBuffer(" --- buffer # " + (count++), buffer);
        }
        logger.debug(" ---");
    }
}
