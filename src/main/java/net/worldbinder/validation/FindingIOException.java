package net.worldbinder.validation;

/** File errors retain their language key when nested in persisted validation findings. */
public final class FindingIOException extends java.io.IOException {
    private final Finding finding;
    public FindingIOException(Finding finding){super(finding.render());this.finding=finding;}
    public FindingIOException(Finding finding,Throwable cause){super(finding.render(),cause);this.finding=finding;}
    public Finding finding(){return finding;}
    @Override public String getMessage(){return finding==null?super.getMessage():finding.render();}
}
