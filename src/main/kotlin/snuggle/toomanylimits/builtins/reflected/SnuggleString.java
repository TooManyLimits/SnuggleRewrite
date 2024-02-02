package snuggle.toomanylimits.builtins.reflected;

import snuggle.toomanylimits.errors.SnuggleException;
import snuggle.toomanylimits.reflection.annotations.SnuggleAllow;
import snuggle.toomanylimits.reflection.annotations.SnuggleRename;
import snuggle.toomanylimits.reflection.annotations.Unsigned;

import java.lang.invoke.MethodHandles;

@SnuggleRename("String")
public class SnuggleString {

    public static final SnuggleString EMPTY = new SnuggleString("");

    // Do not give Snuggle access to .chars field, because then they can mutate it.
    // Instead we need custom methods in here to allow reading the .chars() field, but
    // not writing to it
    private final char[] chars;
    @SnuggleAllow public final @Unsigned int start;
    @SnuggleAllow public final @Unsigned int length;

    @SnuggleAllow
    @SnuggleRename("new")
    public static SnuggleString create(char[] chars, @Unsigned int start, @Unsigned int length) throws SnuggleException {
        return new SnuggleString(chars, start, length);
    }

    // Get the Nth char in this string
    @SnuggleAllow
    @SnuggleRename("get")
    public char charAt(@Unsigned int index) throws SnuggleException {
        if (Integer.compareUnsigned(index, length) >= 0)
            throw new SnuggleException("Invalid string index " + Integer.toUnsignedString(index) + ": String only has length " + length);
        return chars[start + index];
    }

    // Get a substring
    @SnuggleAllow
    @SnuggleRename("get")
    public SnuggleString substring(@Unsigned int begin, @Unsigned int end) throws SnuggleException {
        if (end == begin) return EMPTY;
        // Make sure that:
        // 1. begin <= end
        // 2. start + begin >= start (begin >= 0) (Note: Satisfied by begin <= length)
        // 3. start + begin <= start + length (begin <= length) (Note: Satisfied by 1 and 4)
        // 4. end <= length
        if (Integer.compareUnsigned(begin, end) > 0)
            throw new SnuggleException("Invalid substring: end index " + Integer.toUnsignedString(end) + " is before start index " + Integer.toUnsignedString(begin));
//        if (Integer.compareUnsigned(begin, length) > 0)
//            throw new SnuggleException("Invalid substring: start index " + Integer.toUnsignedString(begin) + " is after the end of the string");
        if (Integer.compareUnsigned(end, length) > 0)
            throw new SnuggleException("Invalid substring: end index " + Integer.toUnsignedString(end) + " is after the end of the string");
        // Now we can just happily add the values and continue along
        // I'm sure there absolutely aren't any strange integer overflow or signed/unsigned errors here
        return new SnuggleString(chars, start + begin, start + end - begin);
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
        if (!(other instanceof SnuggleString)) return false;
        SnuggleString s = (SnuggleString) other;
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
