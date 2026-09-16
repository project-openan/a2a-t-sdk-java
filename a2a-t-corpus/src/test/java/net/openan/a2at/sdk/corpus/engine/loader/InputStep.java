package net.openan.a2at.sdk.corpus.engine.loader;

import java.util.Map;

/** One SDK API step; {@code args} carries the request arguments with optional {@code $fromStep} references. */
public record InputStep(String api, Map<String, Object> args) {}