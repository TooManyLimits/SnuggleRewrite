import reflection.annotations.SnuggleAllow;
import reflection.annotations.SnuggleRename;
import reflection.annotations.SnuggleStatic;

@SnuggleRename("print")
@SnuggleStatic
public class EvilPrinter {

    private String extra;

    public EvilPrinter(String extraMessage) {
        this.extra = extraMessage;
    }

    @SnuggleAllow
    public void invoke(String message) {
        System.out.println(message + extra);
    }

    @SnuggleAllow
    public void invoke(int i) {
        System.out.println(i + extra);
    }

}
