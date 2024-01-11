package representation.passes.lowering

import representation.asts.ir.GeneratedField
import representation.asts.ir.GeneratedMethod
import representation.asts.ir.GeneratedType
import representation.asts.ir.Instruction
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import util.ConsList
import util.caching.IdentityIncrementalCalculator

fun lowerTypeDef(typeDef: TypeDef, typeCalc: IdentityIncrementalCalculator<TypeDef, GeneratedType>): Unit =
    typeCalc.compute(typeDef.unwrap()) {
        when (it) {
            // Generate types for snuggle-defined types like ClassDef
            is TypeDef.ClassDef -> {
                lowerTypeDef(it.supertype, typeCalc)
                GeneratedType.GeneratedClass(
                    it.runtimeName,
                    it.supertype.runtimeName!!,
                    it.fields.map { GeneratedField(it, it.static, it.name).also { lowerTypeDef(it.fieldDef.type, typeCalc) } },
                    // Lower the SnuggleMethodDefs as well as any GenericSnuggleMethodDefs!
                    it.methods.filterIsInstance<MethodDef.SnuggleMethodDef>().map { lowerMethod(it, typeCalc) }
                    + it.methods.filterIsInstance<MethodDef.GenericMethodDef.GenericSnuggleMethodDef>().flatMap {
                        it.specializations.freeze().values.map { lowerMethod(it, typeCalc) }
                    }
                )
            }
            is TypeDef.Tuple, is TypeDef.StructDef -> {
                if (it.fields.isEmpty() && it.methods.isEmpty()) null
                else {
                    GeneratedType.GeneratedValueType(
                        it.runtimeName!!,
                        it.recursiveNonStaticFields.drop(1).map { (pathToField, field) -> GeneratedField(field, true, "RETURN! $$pathToField") },
                        it.staticFields.map { GeneratedField(it, true, it.name).also { lowerTypeDef(it.fieldDef.type, typeCalc) } },
                        it.methods.filterIsInstance<MethodDef.SnuggleMethodDef>().map { lowerMethod(it, typeCalc) }
                        + it.methods.filterIsInstance<MethodDef.GenericMethodDef.GenericSnuggleMethodDef>().flatMap {
                            it.specializations.freeze().values.map { lowerMethod(it, typeCalc) }
                        }
                    )
                }
            }
            is TypeDef.InstantiatedBuiltin -> null //TODO, certain builtins should generate runtime classes
            // Indirections should not be here, we called .unwrap()
            is TypeDef.Indirection -> throw IllegalStateException("Unwrap should have removed indirections? Bug in compiler, please report")
        }
    }

private fun lowerMethod(methodDef: MethodDef.SnuggleMethodDef, typeCalc: IdentityIncrementalCalculator<TypeDef, GeneratedType>): GeneratedMethod {
    // Lower type defs for arg types and return type
    methodDef.paramTypes.forEach { lowerTypeDef(it, typeCalc) }
    lowerTypeDef(methodDef.returnType, typeCalc)

    val loweredBody = lowerExpr(methodDef.lazyBody.value, ConsList.nil(), typeCalc)
    val codeBlock = Instruction.CodeBlock(ConsList.fromIterable(loweredBody.asIterable()))
    return GeneratedMethod(methodDef, codeBlock)
}