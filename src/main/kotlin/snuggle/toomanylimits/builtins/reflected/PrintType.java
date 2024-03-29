package snuggle.toomanylimits.builtins.reflected;

import snuggle.toomanylimits.reflection.annotations.SnuggleAllow;
import snuggle.toomanylimits.reflection.annotations.SnuggleDeny;
import snuggle.toomanylimits.reflection.annotations.SnuggleRename;
import snuggle.toomanylimits.reflection.annotations.Unsigned;

// TODO: Make this only print strings, and have a generic
// TODO: method to print other things that have a .string()
@SnuggleAllow
@SnuggleRename("print")
public class PrintType {

    private PrintType() {}

    public static void invoke(Object arg) { System.out.println(arg); }

    public static void invoke(boolean arg) { System.out.println(arg); }
    public static void invoke(char arg) { System.out.println(arg); }

    public static void invoke(float arg) { System.out.println(arg); }
    public static void invoke(double arg) { System.out.println(arg); }

    public static void invoke(byte arg) { System.out.println(arg); }
    public static void invoke(short arg) { System.out.println(arg); }
    public static void invoke(int arg) { System.out.println(arg); }
    public static void invoke(long arg) { System.out.println(arg); }

    @SnuggleRename("invoke") public static void invoke_u8(@Unsigned(8) int arg) { System.out.println(arg); }
    @SnuggleRename("invoke") public static void invoke_u16(@Unsigned(16) int arg) { System.out.println(arg); }
    @SnuggleRename("invoke") public static void invoke_u32(@Unsigned int arg) { System.out.println(((long) arg) & 0xFFFFFFFFL); }
    @SnuggleRename("invoke") public static void invoke_u64(@Unsigned long arg) { System.out.println(Long.toUnsignedString(arg)); }

}
