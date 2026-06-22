package net.openan.a2at.sample.client.flow;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import net.openan.a2at.sample.shared.error.ValueErrorException;
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
                    Thread.currentThread().interrupt();
                    finished = true;
                    return END;
                }
            }
        };
    }
}
