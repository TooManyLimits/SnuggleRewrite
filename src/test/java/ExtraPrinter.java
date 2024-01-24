import reflection.annotations.SnuggleAllow;
import reflection.annotations.SnuggleRename;
import reflection.annotations.SnuggleStatic;

@SnuggleRename("print")
@SnuggleStatic
public class ExtraPrinter {

    private String extra;

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
