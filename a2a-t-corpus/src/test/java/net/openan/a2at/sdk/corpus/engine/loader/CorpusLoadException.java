package net.openan.a2at.sdk.corpus.engine.loader;

/** Raised when one corpus case file fails strict load-time validation; the message lists every violation. */
public final class CorpusLoadException extends RuntimeException {

    public CorpusLoadException(String message) {
        super(message);
    }
}