package representation.passes.lowering

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import representation.asts.ir.GeneratedType
import representation.asts.ir.Instruction
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedFalliblePattern
import representation.asts.typed.TypedInfalliblePattern
import util.caching.EqualityIncrementalCalculator


/**
 * The scrutinee of the pattern is on the stack.
 * At the end, a boolean will be on the stack, and local variables
 * will be stored (if possible).
 */
fun testFalliblePattern(pattern: TypedFalliblePattern, filesWithEffects: Set<String>, typeCalc: EqualityIncrementalCalculator<TypeDef, GeneratedType>): Sequence<Instruction> {
    return when (pattern) {
        is TypedFalliblePattern.LiteralPattern -> sequence {
            lowerTypeDef(pattern.literalType, filesWithEffects, typeCalc)
            yield(Instruction.Push(pattern.value, pattern.literalType)) // Push our value
            yield(Instruction.TestEquality(pattern.literalType)) // Test for equality
        }
        is TypedFalliblePattern.IsType -> sequence {
            lowerTypeDef(pattern.typeToCheck, filesWithEffects, typeCalc)
            if (pattern.typeToCheck.isReferenceType) {
                if (pattern.varName != null) {
                    // Pattern has a name, so we need to bind a new variable:
                    yield(Instruction.DupRef) // Dup the reference type, stack = [ref, ref]
                    yield(Instruction.StoreLocal(pattern.variableIndex, pattern.typeToCheck)) // stack = [ref]
                }
                // Reference type, we emit an instanceof:
                yield(Instruction.InstanceOf(pattern.typeToCheck))
            } else if (pattern.typeToCheck.isPlural) {
                // If pattern has a var name, store in variable. Otherwise pop it.
                if (pattern.varName != null) {
                    var curIndex = pattern.variableIndex + pattern.typeToCheck.stackSlots
                    pattern.typeToCheck.recursivePluralFields.asReversed().forEach {(_, fieldDef) ->
                        curIndex -= fieldDef.type.stackSlots
                        yield(Instruction.StoreLocal(curIndex, fieldDef.type))
                    }
                } else {
                    yield(Instruction.Pop(pattern.typeToCheck))
                }
                // Now just push true.
                yield(Instruction.Bytecodes(0) { it.visitInsn(Opcodes.ICONST_1) })
            } else {
                // We know it matches. If we have a name, store the value, otherwise pop it.
                if (pattern.varName != null) {
                    yield(Instruction.StoreLocal(pattern.variableIndex, pattern.typeToCheck))
                } else {
                    yield(Instruction.Pop(pattern.typeToCheck))
                }
                // Now just push true.
                yield(Instruction.Bytecodes(0) { it.visitInsn(Opcodes.ICONST_1) })
            }
        }
        is TypedFalliblePattern.Tuple -> sequence {
            // Lower the inner elements in reverse order, and make sure they all succeed:
            val failed = Label()
            val done = Label()
            pattern.elements.asReversed().forEach {
                // Test the inner, fallible pattern to see if it's true:
                yieldAll(testFalliblePattern(it, filesWithEffects, typeCalc))
                // If it's false, jump out of here and emit false.
                yield(Instruction.JumpIfFalse(failed))
            }
            // If it succeeded, push true and end.
            yield(Instruction.Bytecodes(0) { it.visitInsn(Opcodes.ICONST_1) })
            yield(Instruction.Jump(done))
            // If it failed, push false.
            yield(Instruction.IrLabel(failed))
            yield(Instruction.Bytecodes(0) { it.visitInsn(Opcodes.ICONST_0) })
            // Done label.
            yield(Instruction.IrLabel(done))
        }
    }
}

/**
 * The scrutinee of the pattern is currently on the stack.
 * At the end, nothing will be on the stack, and local variables will
 * have been stored.
 */
fun storeInfalliblePattern(pattern: TypedInfalliblePattern, filesWithEffects: Set<String>, typeCalc: EqualityIncrementalCalculator<TypeDef, GeneratedType>): Sequence<Instruction> {
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
                yieldAll(storeInfalliblePattern(it, filesWithEffects, typeCalc))
            }
        }
    }
}