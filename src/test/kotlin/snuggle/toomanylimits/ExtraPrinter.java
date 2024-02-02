package snuggle.toomanylimits;

import snuggle.toomanylimits.reflection.annotations.SnuggleAllow;
import snuggle.toomanylimits.reflection.annotations.SnuggleRename;
import snuggle.toomanylimits.reflection.annotations.SnuggleStatic;

@SnuggleRename("print")
@SnuggleStatic
public class ExtraPrinter {

    private final String extra;

    public ExtraPrinter(String extraMessage) {
        this.extra = extraMessage;
    }

    @SnuggleAllow //print(string)
    public void invoke(String message) {
        System.out.println(message + extra);
    }

    @SnuggleAllow //print(i32)
    public void invoke(int i) {
        System.out.println(i + extra);
    }
}
