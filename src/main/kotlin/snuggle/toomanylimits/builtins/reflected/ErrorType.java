package snuggle.toomanylimits.builtins.reflected;

import snuggle.toomanylimits.errors.SnuggleException;
import snuggle.toomanylimits.reflection.annotations.SnuggleAllow;
import snuggle.toomanylimits.reflection.annotations.SnuggleRename;
import snuggle.toomanylimits.reflection.annotations.Unsigned;

// Simple error() function to error at runtime.
@SnuggleRename("error")
public class ErrorType {

    private ErrorType() {}

    @SnuggleAllow
    public static void invoke(SnuggleString message) throws SnuggleException {
        throw new SnuggleException(message.toString());
    }

}
