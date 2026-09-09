package net.openan.a2at.sdk.negotiation.content;

/**
 * Action a feasibility negotiation propose message performs.
 *
 * <p>The action is not rendered into the message; it selects which conditional sections of the feasibility propose
 * template are emitted.
 *
 * @since 2026-08
 */
public enum NegotiationAction {

    /** Asks the counterpart to evaluate whether the request is feasible. */
    REQUEST_FEASIBILITY_EVALUATION,

    /** Reports that the request is infeasible and proposes an alternative. */
    PROPOSE_ALTERNATIVE_ON_FAILURE
}
