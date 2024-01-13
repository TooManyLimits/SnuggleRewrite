package representation.passes.lowering

import representation.asts.ir.GeneratedField
import representation.asts.ir.GeneratedMethod
import representation.asts.ir.GeneratedType
import representation.asts.ir.Instruction
import representation.asts.parsed.ParsedType
import representation.asts.typed.FieldDef
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
                    it.fields.flatMap { lowerField(it, it.static, it.name, typeCalc) }, //Flatmap generate the fields
                    // Lower the SnuggleMethodDefs as well as any GenericSnuggleMethodDefs!
                    it.methods.filterIsInstance<MethodDef.SnuggleMethodDef>().map { lowerMethod(it, typeCalc) }
                    + it.methods.filterIsInstance<MethodDef.GenericMethodDef.GenericSnuggleMethodDef>().flatMap {
                        it.specializations.freeze().values.map { lowerMethod(it, typeCalc) }
                    }
                )
            }
            is TypeDef.Tuple, is TypeDef.StructDef, is TypeDef.InstantiatedBuiltin -> {
                if (it.fields.isEmpty() && it.methods.isEmpty()) null
                else if (it is TypeDef.InstantiatedBuiltin && !it.shouldGenerateClassAtRuntime) null
                else {
                    GeneratedType.GeneratedValueType(
                        it.runtimeName!!,
                        it.recursiveNonStaticFields.drop(1).map { (pathToField, field) ->
                            lowerTypeDef(field.type, typeCalc)
                            GeneratedField(field, true, "RETURN! $$pathToField")
                        },
                        it.staticFields.flatMap { lowerField(it, it.static, it.name, typeCalc) },
                        it.methods.filterIsInstance<MethodDef.SnuggleMethodDef>().map { lowerMethod(it, typeCalc) }
                        + it.methods.filterIsInstance<MethodDef.GenericMethodDef.GenericSnuggleMethodDef>().flatMap {
                            it.specializations.freeze().values.map { lowerMethod(it, typeCalc) }
                        }
                    )
                }
            }
            is TypeDef.Func -> TODO()
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

private fun lowerField(fieldDef: FieldDef, runtimeStatic: Boolean, runtimeNamePrefix: String, typeCalc: IdentityIncrementalCalculator<TypeDef, GeneratedType>): List<GeneratedField> {
    // Lower type def for the field
    lowerTypeDef(fieldDef.type, typeCalc)
    return if (fieldDef.type.isPlural) {
        // If plural, lower the recursive fields
        fieldDef.type.recursiveNonStaticFields.map { (pathToField, field) ->
            lowerTypeDef(field.type, typeCalc)
            GeneratedField(field, runtimeStatic, runtimeNamePrefix + "$" + pathToField)
        }
    } else {
        // Otherwise, just the one field
        listOf(GeneratedField(fieldDef, runtimeStatic, runtimeNamePrefix))
    }
}