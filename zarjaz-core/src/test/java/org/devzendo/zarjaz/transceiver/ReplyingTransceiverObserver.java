package org.devzendo.zarjaz.transceiver;

import org.devzendo.zarjaz.nio.ReadableByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
public class ReplyingTransceiverObserver implements TransceiverObserver {
    private static final Logger logger = LoggerFactory.getLogger(ReplyingTransceiverObserver.class);

    private final List<ReadableByteBuffer> buffersToReplyWith;

    public ReplyingTransceiverObserver(List<ReadableByteBuffer> buffersToReplyWith) {
        this.buffersToReplyWith = buffersToReplyWith;
    }

    @Override
    public void eventOccurred(final TransceiverObservableEvent observableEvent) {
        try {
            logger.debug("ReplyingTransceiverObserver got data " + observableEvent.getData() + " - replying");
            observableEvent.getReplyWriter().writeBuffer(buffersToReplyWith);
            logger.debug("replied");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
