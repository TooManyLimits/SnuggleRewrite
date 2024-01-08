package representation.passes.lowering

import representation.asts.ir.GeneratedType
import representation.asts.ir.Instruction
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedPattern
import util.IdentityIncrementalCalculator

/**
 * For now, there are no fallible patterns. So do nothing.
 */
fun testPattern(pattern: TypedPattern): Sequence<Instruction> {
    //TODO
    return emptySequence()
}

fun computeTypes(pattern: TypedPattern, typeCalc: IdentityIncrementalCalculator<TypeDef, GeneratedType>) = when (pattern) {
    is TypedPattern.BindingPattern -> lowerTypeDef(pattern.type, typeCalc)
}