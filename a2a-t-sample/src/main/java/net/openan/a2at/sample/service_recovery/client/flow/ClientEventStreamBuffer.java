/*
 * Copyright (c) 2026 Huawei Technologies Co., Ltd.
 * All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package net.openan.a2at.sample.service_recovery.client.flow;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import net.openan.a2at.sample.service_recovery.shared.error.ValueErrorException;
import org.a2aproject.sdk.client.ClientEvent;

/**
 * Bridges async a2a-java client callbacks into a blocking iterable stream.
 *
 * @since 2026-05
 */
public final class ClientEventStreamBuffer implements Iterable<ClientEvent> {
    private static final Object END = new Object();

    private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();

    public void append(ClientEvent event) {
        queue.add(event);
    }

    public void fail(Throwable throwable) {
        queue.add(throwable);
        queue.add(END);
    }

    public void close() {
        queue.add(END);
    }

    @Override
    public Iterator<ClientEvent> iterator() {
        return new Iterator<>() {
            private Object nextItem;
            private boolean finished;

            @Override
            public boolean hasNext() {
                if (finished) {
                    return false;
                }
                if (nextItem == null) {
                    nextItem = takeNextItem();
                }
                if (nextItem == END) {
                    finished = true;
                    return false;
                }
                return true;
            }

            @Override
            public ClientEvent next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                Object resolved = nextItem;
                nextItem = null;
                if (resolved instanceof Throwable throwable) {
                    throw new ValueErrorException("A2A message:stream request failed: " + throwable.getMessage());
                }
                if (resolved instanceof ClientEvent event) {
                    return event;
                }
                throw new ValueErrorException("A2A message:stream returned an unexpected event type.");
            }

            private Object takeNextItem() {
                try {
                    Object item = queue.take();
                    if (item instanceof Throwable throwable) {
                        throw new ValueErrorException("A2A message:stream request failed: " + throwable.getMessage());
                    }
                    return item;
                } catch (InterruptedException exception) {
                    finished = true;
                    return END;
                }
            }
        };
    }
}
