package representation.passes.lowering

import representation.asts.ir.GeneratedType
import representation.asts.ir.Instruction
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedFalliblePattern
import representation.asts.typed.TypedInfalliblePattern
import util.caching.EqualityIncrementalCalculator


/**
 * The scrutinee of the pattern is on the stack.
 */
fun testFalliblePattern(pattern: TypedFalliblePattern): Sequence<Instruction> {
    TODO()
}

/**
 * The scrutinee of the pattern is currently on the stack.
 */
fun lowerInfalliblePattern(pattern: TypedInfalliblePattern, filesWithEffects: Set<String>, typeCalc: EqualityIncrementalCalculator<TypeDef, GeneratedType>): Sequence<Instruction> {
    return when (pattern) {
        is TypedInfalliblePattern.Empty -> {
            // Lower the involved type, pop it off the stack
            lowerTypeDef(pattern.type, filesWithEffects, typeCalc)
            sequenceOf(Instruction.Pop(pattern.type))
        }
        is TypedInfalliblePattern.Binding -> {
            lowerTypeDef(pattern.type, filesWithEffects, typeCalc)
            if (pattern.type.isPlural) sequence {
                var curIndex = pattern.variableIndex + pattern.type.stackSlots
                pattern.type.recursivePluralFields.asReversed().forEach { (_, fieldDef) ->
                    curIndex -= fieldDef.type.stackSlots
                    yield(Instruction.StoreLocal(curIndex, fieldDef.type))
                }
            } else sequenceOf(
                Instruction.StoreLocal(pattern.variableIndex, pattern.type)
            )
        }
        is TypedInfalliblePattern.Tuple -> sequence {
            // Lower the inner patterns in reverse order
            pattern.elements.asReversed().forEach {
                yieldAll(lowerInfalliblePattern(it, filesWithEffects, typeCalc))
            }
        }
    }
}