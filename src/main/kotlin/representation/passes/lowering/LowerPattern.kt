package representation.passes.lowering

import representation.asts.ir.Instruction
import representation.asts.typed.TypedPattern
import util.ConsList

/**
 * For now, there are no fallible patterns. So do nothing.
 */
fun testPattern(pattern: TypedPattern): Sequence<Instruction> {
    //TODO
    return emptySequence()
}