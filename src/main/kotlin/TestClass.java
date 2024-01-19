import reflection.annotations.SnuggleAllow;
import reflection.annotations.Unsigned;

@SnuggleAllow
public class TestClass {

    public String x = "hi";
    public int y = -10;
    public @Unsigned int z = -20;

    public static TestClass getInstance() {
        return new TestClass();
    }

}