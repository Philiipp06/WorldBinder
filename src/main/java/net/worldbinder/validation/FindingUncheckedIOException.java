package net.worldbinder.validation;

/** Preserve UncheckedIOException handling and the translated context of a failed file operation. */
public final class FindingUncheckedIOException extends java.io.UncheckedIOException {
    private final Finding finding;

    public FindingUncheckedIOException(Finding finding, java.io.IOException cause) {
        super(finding.render(), cause);
        this.finding = finding;
    }
    public Finding finding() { return finding; }
    @Override public String getMessage() { return finding == null ? super.getMessage() : finding.render(); }
}
