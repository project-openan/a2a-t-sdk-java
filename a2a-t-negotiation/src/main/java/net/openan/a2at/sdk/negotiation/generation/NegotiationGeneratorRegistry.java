package net.openan.a2at.sdk.negotiation.generation;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatch table from negotiation type and phase to the generator serving them.
 *
 * <p>Dispatch requires an exact runtime type match between the content and the addressed (type, phase) pair: propose
 * phases only accept propose content, terminal phases only accept ending content whose conclusion matches the phase,
 * and the content type must match the negotiation type. The abort phase dispatches to the type-independent abort
 * generator and requires abort content. Subtype matching is deliberately not supported; new content types must be
 * registered explicitly.
 *
 * @since 2026-08
 */
final class NegotiationGeneratorRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(NegotiationGeneratorRegistry.class);

    private final Map<NegotiationType, NegotiationGenerator> proposeGenerators = new EnumMap<>(NegotiationType.class);

    private final Map<NegotiationType, NegotiationGenerator> endingGenerators = new EnumMap<>(NegotiationType.class);

    private final NegotiationGenerator abortGenerator = new AbortGenerator();

    /** Creates a registry holding the built-in negotiation generators. */
    public NegotiationGeneratorRegistry() {
        proposeGenerators.put(NegotiationType.INFORMATION, new InformationProposeGenerator());
        proposeGenerators.put(NegotiationType.TARGET, new TargetProposeGenerator());
        proposeGenerators.put(NegotiationType.FEASIBILITY, new FeasibilityProposeGenerator());
        endingGenerators.put(NegotiationType.INFORMATION, new InformationEndingGenerator());
        endingGenerators.put(NegotiationType.TARGET, new TargetEndingGenerator());
        endingGenerators.put(NegotiationType.FEASIBILITY, new FeasibilityEndingGenerator());
    }

    /**
     * Resolves the generator for one (type, phase, content) triple.
     *
     * @param type negotiation type addressed by the template URI; null only for the type-independent abort phase
     * @param phase API-level phase addressed by the calling method
     * @param content typed content of the message
     * @param language message language used to render the failure message of a conclusion mismatch
     * @return generator registered for the exact (type, phase) pair
     * @throws NullPointerException if the phase or content is null, or the type is null on a typed phase
     * @throws IllegalArgumentException if the content family does not match the phase or the content runtime type does
     *     not match the negotiation type
     * @throws NegotiationGenerationException with the code {@code negotiation.conclusion_mismatch} if an ending content
     *     carries a conclusion that does not match the phase
     */
    public NegotiationGenerator resolve(
            @Nullable NegotiationType type,
            NegotiationPerformative phase,
            NegotiationContent content,
            String language) {
        Objects.requireNonNull(phase, "Negotiation phase must not be null.");
        Objects.requireNonNull(content, "Negotiation content must not be null.");
        if (phase == NegotiationPerformative.ABORT) {
            if (type != null) {
                throw new IllegalArgumentException(
                        "The ABORT phase is type-independent and must not carry a type but carried " + type + ".");
            }
            if (content.getClass() != NegotiationAbortContent.class) {
                throw new IllegalArgumentException("The ABORT phase requires abort content but received "
                        + content.getClass().getSimpleName() + ".");
            }
            LOGGER.atDebug().log(
                    "negotiation_generator_dispatched generator=AbortGenerator type=common performative=ABORT");
            return abortGenerator;
        }
        Objects.requireNonNull(type, "Negotiation type must not be null for the " + phase + " phase.");
        boolean proposePhase = phase == NegotiationPerformative.PROPOSE;
        boolean proposeContent = content instanceof NegotiationProposeContent;
        if (proposePhase != proposeContent) {
            throw new IllegalArgumentException(
                    "The " + phase + " phase requires " + (proposePhase ? "propose" : "ending")
                            + " content but received "
                            + (proposeContent ? "propose" : "ending") + " content of type "
                            + content.getClass().getSimpleName() + ".");
        }
        Class<?> expectedType = expectedContentClass(type, proposePhase);
        if (content.getClass() != expectedType) {
            throw new IllegalArgumentException(
                    "Negotiation type " + type + " requires content of type " + expectedType.getSimpleName()
                            + " but received " + content.getClass().getSimpleName() + ".");
        }
        if (!proposePhase) {
            requireConclusionMatchesPhase((NegotiationEndingContent) content, phase, language);
        }
        NegotiationGenerator generator = (proposePhase ? proposeGenerators : endingGenerators).get(type);
        if (generator == null) {
            throw new IllegalArgumentException(
                    "No negotiation generator is registered for type " + type + " and phase " + phase + ".");
        }
        LOGGER.atDebug().log(
                "negotiation_generator_dispatched generator={} type={} performative={}",
                generator.getClass().getSimpleName(),
                type,
                phase);
        return generator;
    }

    private static void requireConclusionMatchesPhase(
            NegotiationEndingContent content, NegotiationPerformative phase, String language) {
        NegotiationConclusion conclusion = Objects.requireNonNull(
                content.conclusion(),
                "Negotiation conclusion must not be null; the " + phase + " phase requires a conclusion.");
        NegotiationConclusion expected =
                phase == NegotiationPerformative.ACCEPT ? NegotiationConclusion.ACCEPT : NegotiationConclusion.REJECT;
        if (conclusion != expected) {
            throw new NegotiationGenerationException(
                    ErrorCatalog.NEGOTIATION_CONCLUSION_MISMATCH,
                    language,
                    Map.of("expected", expected.literal(), "actual", conclusion.literal()));
        }
    }

    private static Class<? extends NegotiationContent> expectedContentClass(
            NegotiationType type, boolean proposePhase) {
        if (proposePhase) {
            return switch (type) {
                case INFORMATION -> InformationProposeContent.class;
                case TARGET -> TargetProposeContent.class;
                case FEASIBILITY -> FeasibilityProposeContent.class;
            };
        }
        return switch (type) {
            case INFORMATION -> InformationEndingContent.class;
            case TARGET -> TargetEndingContent.class;
            case FEASIBILITY -> FeasibilityEndingContent.class;
        };
    }
}
