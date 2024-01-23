package builtins

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import representation.asts.typed.FieldDef
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.passes.typing.TypeDefCache
import representation.passes.typing.getBasicBuiltin
import representation.passes.typing.getGenericBuiltin
import representation.passes.typing.getUnit
import util.ConsList

object ArrayType: BuiltinType {
    override val numGenerics: Int get() = 1

    override val baseName: String get() = "Array"
    override fun name(generics: List<TypeDef>, typeCache: TypeDefCache): String = generics[0].name + "[]"
    override val nameable: Boolean get() = true
    override fun runtimeName(generics: List<TypeDef>, typeCache: TypeDefCache): String? {
        if (generics[0].isPlural && generics[0].stackSlots > 0)
            return "builtin_structs/array/" + generics[0].name
        return null
    }
    override fun descriptor(generics: List<TypeDef>, typeCache: TypeDefCache): List<String> {
        if (generics[0].stackSlots > 0)
            return generics[0].descriptor.map { "[$it" }
        return listOf("I")
    }
    override fun stackSlots(generics: List<TypeDef>, typeCache: TypeDefCache): Int {
        if (generics[0].isPlural && generics[0].stackSlots > 0)
            return generics[0].recursiveNonStaticFields.size
        return 1
    }
    override fun isPlural(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = generics[0].isPlural
    override fun isReferenceType(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = !generics[0].isPlural
    override fun hasStaticConstructor(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = true

    override fun getFields(generics: List<TypeDef>, typeCache: TypeDefCache): List<FieldDef> {
        val u32 = getBasicBuiltin(U32Type, typeCache)
        if (generics[0].stackSlots == 0)
            return listOf(FieldDef.BuiltinField(false, false, false, "length", 0, u32))
        // If plural, then the inner fields are arrays of each element of the plural inner type
        if (generics[0].isPlural) {
            var curIndex = 0
            return generics[0].nonStaticFields.map {
                val wrappedInArray = getGenericBuiltin(ArrayType, listOf(it.type), typeCache)
                FieldDef.BuiltinField(false, false, false, "inner_${it.name}", curIndex, wrappedInArray)
                    .also { curIndex += wrappedInArray.stackSlots }
            }
        }
        return listOf()
    }

    override fun getMethods(generics: List<TypeDef>, typeCache: TypeDefCache): List<MethodDef> {
        // Grab some useful types
        val thisType = getGenericBuiltin(ArrayType, generics, typeCache)
        val innerType = generics[0]
        val u32 = getBasicBuiltin(U32Type, typeCache)
        val unit = getUnit(typeCache)
        // Methods work differently depending on inner type
        return when {
            innerType.stackSlots == 0 -> zeroSizeMethods(thisType, innerType, u32, unit)
            innerType.isPlural -> pluralMethods(thisType, innerType, u32, unit)
            else -> singletMethods(thisType, innerType, u32, unit)
        }
    }

    // When the type is a primitive or a reference type, use this.
    private fun singletMethods(thisType: TypeDef, innerType: TypeDef, u32: TypeDef, unit: TypeDef): List<MethodDef> {
        val opcodes = getOpcodesFor(innerType)
        // Only have the constructor(size) if it's an optional reference type, or is primitive
        val constructor = if (!opcodes.isReferenceType || innerType.isOptionalReferenceType)
            MethodDef.BytecodeMethodDef(pub = true, static = true, thisType, "new", thisType, listOf(u32)) {
                if (opcodes.isReferenceType)
                    it.visitTypeInsn(Opcodes.ANEWARRAY, innerType.runtimeName)
                else
                    it.visitIntInsn(Opcodes.NEWARRAY, opcodes.newArg)
            } else null
        return listOfNotNull(
            constructor,
            MethodDef.BytecodeMethodDef(pub = true, static = false, thisType, "size", u32, listOf()) {
                it.visitInsn(Opcodes.ARRAYLENGTH)
            },
            MethodDef.BytecodeMethodDef(pub = true, static = false, thisType, "get", innerType, listOf(u32)) {
                it.visitInsn(opcodes.loadFromArray)
            },
            MethodDef.BytecodeMethodDef(pub = true, static = false, thisType, "set", unit, listOf(u32, innerType)) {
                it.visitInsn(opcodes.storeInArray)
            }
        )
    }

    // Methods used when the inner type is zero-sized. Then
    // the array is stored as just a u32.
    private fun zeroSizeMethods(thisType: TypeDef, innerType: TypeDef, u32: TypeDef, unit: TypeDef): List<MethodDef> {
        fun checkIndex(writer: MethodVisitor) {
            // [thisSize, index]
            writer.visitInsn(Opcodes.DUP_X1) // [index, thisSize, index]
            writer.visitInsn(Opcodes.SWAP) // [index, index, thisSize]
            writer.visitInsn(Opcodes.DUP_X2) // [thisSize, index, index, thisSize]
            writer.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "compareUnsigned", "(II)I", false)
            // [thisSize, index, compare(index, thisSize)]. If index is less than thisSize, it's okay, otherwise not
            val afterError = Label()
            writer.visitJumpInsn(Opcodes.IFLT, afterError)
            // Error! [thisSize, index]
            indexingError(writer)
            writer.visitLabel(afterError) // [thisSize, index]
            writer.visitInsn(Opcodes.POP) // [thisSize]
            writer.visitInsn(Opcodes.POP) // [] (this is also the return value, since it's 0-sized)
        }
        return listOf(
            // new(size) -> no-op, the array is literally just a size, that's it
            MethodDef.BytecodeMethodDef(pub = true, static = true, thisType, "new", thisType, listOf(u32)) {},
            // .size() -> no-op, the array is literally just a size
            MethodDef.BytecodeMethodDef(pub = true, static = false, thisType, "size", u32, listOf()) {},
            // .get() -> check the given index and error if too high, don't need to return anything
            MethodDef.BytecodeMethodDef(pub = true, static = false, thisType, "get", innerType, listOf(u32), ::checkIndex),
            // .set() -> same as .get(), mainly just error if index is too high
            MethodDef.BytecodeMethodDef(pub = true, static = false, thisType, "set", unit, listOf(u32, innerType), ::checkIndex)
        )
    }

    // Used when the inner type is plural (but not zero-sized). Decomposes the array
    // into multiple smaller arrays, one for each field of the plural type.
    private fun pluralMethods(thisType: TypeDef, innerType: TypeDef, u32: TypeDef, unit: TypeDef): List<MethodDef> {
        // If the type contains any reference types, then it can't be instantiated via new().
        val containsAnyReferenceType = innerType.recursiveNonStaticFields.any { it.second.type.isReferenceType }
        val constructor = if (!containsAnyReferenceType)
            MethodDef.BytecodeMethodDef(pub = true, static = true, thisType, "new", thisType, listOf(u32), null, { writer, maxVariable, desiredFields ->
                //TODO replace with dup/swap, not local variable
                val needsToStoreLocal = (desiredFields.count() > 1 || desiredFields.any {
                    val innerElemType = it.lastOrNull()?.type ?: innerType
                    innerElemType.isPlural && innerElemType.recursiveNonStaticFields.size > 1
                })
                if (needsToStoreLocal)
                    writer.visitVarInsn(Opcodes.ISTORE, maxVariable)
                for (fieldGroup in desiredFields) {
                    val innerArrayElemType = fieldGroup.lastOrNull()?.type ?: innerType
                    if (innerArrayElemType.isPlural) {
                        for ((_, field) in innerArrayElemType.recursiveNonStaticFields) {
                            if (needsToStoreLocal)
                                writer.visitVarInsn(Opcodes.ILOAD, maxVariable)
                            val opcodes = getOpcodesFor(field.type)
                            if (opcodes.isReferenceType)
                                writer.visitTypeInsn(Opcodes.ANEWARRAY, field.type.runtimeName)
                            else
                                writer.visitIntInsn(Opcodes.NEWARRAY, opcodes.newArg)
                        }
                    } else {
                        if (needsToStoreLocal)
                            writer.visitVarInsn(Opcodes.ILOAD, maxVariable)
                        val opcodes = getOpcodesFor(innerArrayElemType)
                        if (opcodes.isReferenceType)
                            writer.visitTypeInsn(Opcodes.ANEWARRAY, innerArrayElemType.runtimeName)
                        else
                            writer.visitIntInsn(Opcodes.NEWARRAY, opcodes.newArg)
                    }
                }
            }) { throw IllegalStateException() } else null
        return listOfNotNull(
            constructor,
            MethodDef.BytecodeMethodDef(pub = true, static = false, thisType, "size", u32, listOf(), null, { writer, _, _ ->
                writer.visitInsn(Opcodes.ARRAYLENGTH)
            }) {
                var innermostFirstField = thisType.nonStaticFields.first() // Array<innerType>
                while (innermostFirstField.type.isPlural)
                    innermostFirstField = innermostFirstField.type.nonStaticFields.first()
                ConsList.of(ConsList.of(innermostFirstField))
            },
            MethodDef.BytecodeMethodDef(pub = true, static = false, thisType, "get", innerType, listOf(u32), null, {writer, maxVariable, desiredFields ->
                // Stack = [all desired arrays, index]
                // Calculate how many desired arrays there are, and what types they are:
                val desiredInnerTypes = desiredFields.asIterable().flatMap {
                    val lastFieldType = it.lastOrNull()?.type ?: innerType
                    if (lastFieldType.isPlural)
                        lastFieldType.recursiveNonStaticFields.map { it.second.type }
                    else listOf(lastFieldType)
                }
                if (desiredInnerTypes.isEmpty()) throw IllegalStateException("Expected at least 1 desiredInnerType")
                var curMaxVariable = maxVariable
                for (curType in desiredInnerTypes.drop(1).asReversed()) { // Iterate backwards until "first"
                    // Dup index down, fetch from the array, store the result to local variable
                    writer.visitInsn(Opcodes.DUP_X1) // [arrays, index, topArray, index]
                    val opcodes = getOpcodesFor(curType)
                    writer.visitInsn(opcodes.loadFromArray) // [arrays, index, topArray[index]]
                    writer.visitVarInsn(opcodes.storeInLocal, curMaxVariable) // [arrays, index]
                    curMaxVariable += curType.stackSlots // Inc cur max variable
                }
                // Ending case, load from the last(/first) array.
                val firstOpcodes = getOpcodesFor(desiredInnerTypes[0])
                writer.visitInsn(firstOpcodes.loadFromArray) // Just load from the array
                // Now, re-fetch all those local variables
                for (curType in desiredInnerTypes.drop(1)) { // Iterate forwards
                    curMaxVariable -= curType.stackSlots
                    val opcodes = getOpcodesFor(curType)
                    writer.visitVarInsn(opcodes.loadFromLocal, curMaxVariable)
                }
                // Stack = [values from all desired arrays], as it should be :)))
            }) {
                // Map field desires to "inner_" of themselves (name of array fields)
                it.map {
                    var curType = thisType
                    it.map { f ->
                        curType.fields.find { it.name == "inner_" + f.name }!!
                            .also { curType = it.type }
                    }
                }
            },
            MethodDef.BytecodeMethodDef(pub = true, static = false, thisType, "set", unit, listOf(u32, innerType), null, {writer, maxVariable, desiredFields ->
                // Stack = [arr1, ..., arrN, index, elem1, ..., elemN]
                // Part 1: store elem1, ... elemN into local variables.
                var curMaxVariable = maxVariable
                for ((_, field) in innerType.recursiveNonStaticFields.asReversed()) {
                    writer.visitVarInsn(getOpcodesFor(field.type).storeInLocal, curMaxVariable)
                    curMaxVariable += field.type.stackSlots
                }
                curMaxVariable = maxVariable // Reset, as we'll count upwards (increment) again.
                // Stack = [arr1, ..., arrN, index]
                // Part 2: Store elements into the arrays
                for ((_, field) in innerType.recursiveNonStaticFields.asReversed()) {
                    // [arr1, ..., arrI, index]
                    writer.visitInsn(Opcodes.DUP_X1) // [arr1, ..., index, arrI, index]
                    val opcodes = getOpcodesFor(field.type)
                    writer.visitVarInsn(opcodes.loadFromLocal, curMaxVariable) // [arr1, ..., index, arrI, index, elemI]
                    curMaxVariable += field.type.stackSlots
                    writer.visitInsn(opcodes.storeInArray) // [arr1, ..., arrI-1, index]. Now repeat!
                }
                // Stack = [index]
                writer.visitInsn(Opcodes.POP)
            }) { ConsList.of(ConsList.nil()) }
        )
    }
}

private data class ArrayOpcodes(val isReferenceType: Boolean, val newArg: Int, val storeInArray: Int, val loadFromArray: Int, val storeInLocal: Int, val loadFromLocal: Int)
private fun getOpcodesFor(innerType: TypeDef): ArrayOpcodes = when {
    innerType.builtin is IntType -> when ((innerType.builtin as IntType).bits) {
        8 -> ArrayOpcodes(false, Opcodes.T_BYTE, Opcodes.BASTORE, Opcodes.BALOAD, Opcodes.ISTORE, Opcodes.ILOAD)
        16 -> ArrayOpcodes(false, Opcodes.T_SHORT, Opcodes.SASTORE, Opcodes.SALOAD, Opcodes.ISTORE, Opcodes.ILOAD)
        32 -> ArrayOpcodes(false, Opcodes.T_INT, Opcodes.IASTORE, Opcodes.IALOAD, Opcodes.ISTORE, Opcodes.ILOAD)
        64 -> ArrayOpcodes(false, Opcodes.T_LONG, Opcodes.LASTORE, Opcodes.LALOAD, Opcodes.LSTORE, Opcodes.LLOAD)
        else -> throw IllegalStateException()
    }
    innerType.builtin is FloatType -> when ((innerType.builtin as FloatType).bits) {
        32 -> ArrayOpcodes(false, Opcodes.T_FLOAT, Opcodes.FASTORE, Opcodes.FALOAD, Opcodes.FSTORE, Opcodes.FLOAD)
        64 -> ArrayOpcodes(false, Opcodes.T_DOUBLE, Opcodes.DASTORE, Opcodes.DALOAD, Opcodes.DSTORE, Opcodes.DLOAD)
        else -> throw IllegalStateException()
    }
    innerType.builtin == BoolType -> ArrayOpcodes(false, Opcodes.T_BOOLEAN, Opcodes.BASTORE, Opcodes.BALOAD, Opcodes.ISTORE, Opcodes.ILOAD)
    innerType.isReferenceType || (innerType.builtin == OptionType && innerType.generics[0].isReferenceType)
        -> ArrayOpcodes(true, -1, Opcodes.AASTORE, Opcodes.AALOAD, Opcodes.ASTORE, Opcodes.ALOAD)
    else -> throw IllegalStateException("No case for ArrayOpcodes matched? Bug in compiler, please report")
}

// Stack coming into this is [sizeOfThisArray, indexTriedToUse]
private fun indexingError(writer: MethodVisitor) {
    // TODO: Better error
    // Create the message
    writer.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false) //[sizeOfThisArray, "indexTriedToUse"]
    writer.visitLdcInsn("Attempt to access array with index ") //[sizeOfThisArray, "indexTriedToUse", message]
    writer.visitInsn(Opcodes.SWAP) // [sizeOfThisArray, message, "indexTriedToUse"]
    writer.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false) //[sizeOfThisArray, message]
    writer.visitLdcInsn(", but array length was only ") //[sizeOfThisArray, message1, message2]
    writer.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false) //[sizeOfThisArray, message]
    writer.visitInsn(Opcodes.SWAP) // [message, sizeOfThisArray]
    writer.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false) //[message, "sizeOfThisArray"]
    writer.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false) //[message]

    //[message]
    val runtimeException = Type.getInternalName(RuntimeException::class.java)
    writer.visitTypeInsn(Opcodes.NEW, runtimeException) // [message, exception]
    writer.visitInsn(Opcodes.DUP_X1) // [exception, message, exception]
    writer.visitInsn(Opcodes.SWAP) // [exception, exception, message]
    writer.visitMethodInsn(Opcodes.INVOKESPECIAL, runtimeException, "<init>", "(Ljava/lang/String;)V", false) //[exception]
    writer.visitInsn(Opcodes.ATHROW) //[]
}