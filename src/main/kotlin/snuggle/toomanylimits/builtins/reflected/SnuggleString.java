package snuggle.toomanylimits.builtins.reflected;

import snuggle.toomanylimits.errors.SnuggleException;
import snuggle.toomanylimits.reflection.annotations.SnuggleAllow;
import snuggle.toomanylimits.reflection.annotations.SnuggleRename;
import snuggle.toomanylimits.reflection.annotations.Unsigned;

import java.lang.invoke.MethodHandles;

@SnuggleRename("String")
public class SnuggleString {

    @SnuggleAllow public final char[] chars;
    @SnuggleAllow public final @Unsigned int start;
    @SnuggleAllow public final @Unsigned int length;

    @SnuggleAllow
    @SnuggleRename("new")
    public static SnuggleString create(char[] chars, @Unsigned int start, @Unsigned int length) throws SnuggleException {
        return new SnuggleString(chars, start, length);
    }

    public SnuggleString(char[] chars, @Unsigned int start, @Unsigned int length) throws SnuggleException {
        this.chars = chars;
        this.start = start;
        this.length = length;
        if (start < 0 || start > chars.length)
            throw new SnuggleException("Invalid string construction - char[] length is " + chars.length + ", but start pos is " + Integer.toUnsignedString(start));
        if (start + length < 0 || start + length > chars.length)
            throw new SnuggleException("Invalid string construction - char[] length is " + chars.length + ", but end pos is " + (Integer.toUnsignedLong(start) + Integer.toUnsignedLong(length)));
    }

    // Conversion to and from java strings:
    public SnuggleString(String javaString) {
        this.chars = javaString.toCharArray();
        this.start = 0;
        this.length = javaString.length();
    }
    public String toString() {
        return new String(chars, start, length);
    }

    // Java helpers
    public boolean equals(Object other) {
        if (!(other instanceof SnuggleString s)) return false;
        if (length != s.length) return false;
        if (chars == s.chars) return true;
        for (int i = start; i < start + length; i++)
            if (chars[i] != s.chars[i])
                return false;
        return true;
    }

    /**
     * Bootstrap method for ConstantDynamic to generate and cache SnuggleString
     * instances at runtime from java Strings.
     */
    public static SnuggleString bootstrapGenerator(MethodHandles.Lookup lookup, String name, Class<?> type, String actualString) {
        return new SnuggleString(actualString);
    }

}
