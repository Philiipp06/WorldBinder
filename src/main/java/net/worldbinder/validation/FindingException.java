package net.worldbinder.validation;

/** A validation failure retains its message key through chunk/file context instead of freezing one language. */
public final class FindingException extends IllegalArgumentException {
    private final Finding finding;
    public FindingException(Finding finding){super(finding.render());this.finding=finding;}
    public FindingException(Finding finding,Throwable cause){super(finding.render(),cause);this.finding=finding;}
    @Override public String getMessage(){return finding==null?super.getMessage():finding.render();}
    public Finding finding(){return finding;}
}
