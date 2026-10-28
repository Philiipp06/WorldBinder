package net.worldbinder.validation;

/** Runtime state errors remain translatable when persisted or displayed after a language switch. */
public final class FindingStateException extends IllegalStateException {
    private final Finding finding;

    public FindingStateException(Finding finding) { this(finding, null); }
    public FindingStateException(Finding finding, Throwable cause) {
        super(finding.render(), cause);
        this.finding = finding;
    }
    public Finding finding() { return finding; }
    @Override public String getMessage() { return finding == null ? super.getMessage() : finding.render(); }
}
