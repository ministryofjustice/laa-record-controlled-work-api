package uk.gov.justice.laa.rcw.pact;

/**
 * Shared base for consumer Pact tests against the datastore provider.
 *
 * <p>{@link #CONSUMER} must equal this repo's pacticipant name (= the GitHub repo name). {@link
 * #PROVIDER} must byte-for-byte equal the provider's {@code @Provider("...")} value in {@code
 * laa-info-and-advice-datastore} - a mismatch silently creates two separate pacticipants and
 * nothing ever gets verified.
 */
public abstract class AbstractPactTest {

  /** This consumer's pacticipant name. */
  public static final String CONSUMER = "laa-record-controlled-work-api";

  /** The provider we contract with. Must match {@code @Provider("...")} in the provider repo. */
  public static final String PROVIDER = "laa-info-and-advice-datastore";

  // --- reusable format matchers: assert shape, not specific values ---
  public static final String UUID_REGEX =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
}
