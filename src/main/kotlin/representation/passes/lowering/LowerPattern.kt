package representation.passes.lowering

import representation.asts.ir.GeneratedType
import representation.asts.ir.Instruction
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedPattern
import util.caching.IdentityIncrementalCalculator

/**
 * For now, there are no fallible patterns. So do nothing.
 */
fun testPattern(pattern: TypedPattern): Sequence<Instruction> {
    //TODO
    return emptySequence()
}

/**
 * The scrutinee of the pattern is currently on the stack.
 */
fun lowerPattern(pattern: TypedPattern, typeCalc: IdentityIncrementalCalculator<TypeDef, GeneratedType>): Sequence<Instruction> {
    return when (pattern) {
        is TypedPattern.EmptyPattern -> {
            // Lower the involved type, pop it off the stack
            lowerTypeDef(pattern.type, typeCalc)
            sequenceOf(Instruction.Pop(pattern.type))
        }
        is TypedPattern.BindingPattern -> {
            lowerTypeDef(pattern.type, typeCalc)
            if (pattern.type.isPlural) sequence {
                var curIndex = pattern.variableIndex + pattern.type.stackSlots
                pattern.type.recursiveNonStaticFields.asReversed().forEach {(_, fieldDef) ->
                    curIndex -= fieldDef.type.stackSlots
                    yield(Instruction.StoreLocal(curIndex, fieldDef.type))
                }
            } else sequenceOf(
                Instruction.StoreLocal(pattern.variableIndex, pattern.type)
            )
        }
        is TypedPattern.TuplePattern -> sequence {
            // Lower the inner patterns in reverse order
            pattern.elements.asReversed().forEach {
                yieldAll(lowerPattern(it, typeCalc))
            }
        }
    }
}