package representation.passes.lowering

import representation.asts.ir.GeneratedField
import representation.asts.ir.GeneratedMethod
import representation.asts.ir.GeneratedType
import representation.asts.ir.Instruction
import representation.asts.typed.FieldDef
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import util.ConsList
import util.caching.IdentityIncrementalCalculator

fun lowerTypeDef(typeDef: TypeDef, typeCalc: IdentityIncrementalCalculator<TypeDef, GeneratedType>): Unit =
    typeCalc.compute(typeDef.unwrap()) {
        // Generate the methods that need to be
        val generatedMethods: List<GeneratedMethod> =
            it.methods.mapNotNull { getGeneratedMethod(it, typeCalc) }.flatten()
        when (it) {
            // Generate types for snuggle-defined types like ClassDef
            is TypeDef.ClassDef -> {
                lowerTypeDef(it.supertype, typeCalc)
                GeneratedType.GeneratedClass(
                    it.runtimeName,
                    it.supertype.runtimeName!!,
                    it.fields.flatMap { lowerField(it, it.static, it.name, typeCalc) }, //Flatmap generate the fields
                    generatedMethods
                )
            }
            is TypeDef.Tuple, is TypeDef.StructDef, is TypeDef.InstantiatedBuiltin, is TypeDef.ImplBlockBackingDef -> {
                if (it.fields.isEmpty() && generatedMethods.isEmpty()) null
                else if (it is TypeDef.InstantiatedBuiltin && !it.shouldGenerateClassAtRuntime) null
                else if (!it.isPlural) throw IllegalStateException("Only expected plural types to make it this far in lowering for builtins, but \"${it.name}\" did too? Bug in compiler, please report")
                else if (it.recursivePluralFields.size <= 1 && it.staticFields.isEmpty() && generatedMethods.isEmpty()) null
                else {
                    GeneratedType.GeneratedValueType(
                        it.runtimeName!!,
                        it.recursivePluralFields.drop(1).map { (pathToField, field) ->
                            lowerTypeDef(field.type, typeCalc)
                            GeneratedField(field, true, "RETURN! $$pathToField")
                        },
                        it.staticFields.flatMap { lowerField(it, it.static, it.name, typeCalc) },
                        generatedMethods
                    )
                }
            }
            is TypeDef.Func -> GeneratedType.GeneratedFuncType(it.runtimeName, generatedMethods)
            is TypeDef.FuncImplementation -> GeneratedType.GeneratedFuncImpl(
                it.runtimeName, it.primarySupertype.runtimeName!!,
                it.fields.flatMap { lowerField(it, it.static, it.name, typeCalc) },
                generatedMethods
            )
            // Indirections should not be here, we called .unwrap()
            is TypeDef.Indirection -> throw IllegalStateException("Unwrap should have removed indirections? Bug in compiler, please report")
        }
    }

fun getGeneratedMethod(methodDef: MethodDef, typeCalc: IdentityIncrementalCalculator<TypeDef, GeneratedType>): List<GeneratedMethod>? = when (methodDef) {
    is MethodDef.SnuggleMethodDef -> listOf(lowerMethod(methodDef, typeCalc))
    is MethodDef.CustomMethodDef -> listOf(GeneratedMethod.GeneratedCustomMethod(methodDef))
    is MethodDef.InterfaceMethodDef -> listOf(GeneratedMethod.GeneratedInterfaceMethod(methodDef))
    is MethodDef.GenericMethodDef<*> -> methodDef.specializations.freeze().values.mapNotNull { getGeneratedMethod(it, typeCalc) }.flatten()

    is MethodDef.BytecodeMethodDef -> null
    is MethodDef.ConstMethodDef -> null
    is MethodDef.StaticConstMethodDef -> null
}


private fun lowerMethod(methodDef: MethodDef.SnuggleMethodDef, typeCalc: IdentityIncrementalCalculator<TypeDef, GeneratedType>): GeneratedMethod.GeneratedSnuggleMethod {
    // Lower type defs for arg types and return type
    methodDef.paramTypes.forEach { lowerTypeDef(it, typeCalc) }
    lowerTypeDef(methodDef.returnType, typeCalc)

    val loweredBody = lowerExpr(methodDef.lazyBody.value, ConsList.of(ConsList.nil()), typeCalc)
    val codeBlock = Instruction.CodeBlock(loweredBody.toList())
    return GeneratedMethod.GeneratedSnuggleMethod(methodDef, codeBlock)
}

private fun lowerField(fieldDef: FieldDef, runtimeStatic: Boolean, runtimeNamePrefix: String, typeCalc: IdentityIncrementalCalculator<TypeDef, GeneratedType>): List<GeneratedField> {
    // Lower type def for the field
    lowerTypeDef(fieldDef.type, typeCalc)
    return if (fieldDef.type.isPlural) {
        // If plural, lower the recursive fields
        fieldDef.type.recursivePluralFields.map { (pathToField, field) ->
            lowerTypeDef(field.type, typeCalc)
            GeneratedField(field, runtimeStatic, runtimeNamePrefix + "$" + pathToField)
        }
    } else {
        // Otherwise, just the one field
        listOf(GeneratedField(fieldDef, runtimeStatic, runtimeNamePrefix))
    }
}