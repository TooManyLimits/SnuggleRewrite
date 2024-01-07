package representation.passes.typing

import representation.asts.resolved.ResolvedMethodDef
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import util.ConsList
import util.ConsMap
import util.extend

/**
 * Converting members of types, like methods/fields, from
 * ResolvedAST form to TypedAST form.
 */

/**
 * Convert a ResolvedMethodDef to a MethodDef.
 * - owningType: The type that this is a method of, so we know what type to use for "this"
 * - allMethodDefs: The list of all the type-resolved method defs on the owning type.
 *                  It's used to disambiguate method names from one another.
 * - methodDef: The method def that is to be type-checked.
 * - typeCache: The cache of TypeDefs that have been created while typing this AST.
 * - currentTypeGenerics: The generics of the owningType that we're currently instantiating with.
 */
fun typeMethod(owningType: TypeDef, allMethodDefs: List<ResolvedMethodDef>, methodDef: ResolvedMethodDef, typeCache: TypeDefCache, currentTypeGenerics: List<TypeDef>): MethodDef.SnuggleMethodDef {
    var disambiguationIndex = 0
    for (method in allMethodDefs) {
        if (method == methodDef) break;
        if (method.name == methodDef.name) disambiguationIndex++
    }
    // Type the patterns that are the params
    val typedParams = methodDef.params.map { inferPattern(it, typeCache, currentTypeGenerics) }
    // Get the param types that are required to be passed to the function
    val paramTypes = typedParams.map { it.type }
    // Get the bindings for the body, from the params
    var bodyBindings: ConsMap<String, VariableBinding> = ConsMap.of()
    // If non-static, add a "this" parameter as the first one in the bindings
    if (!methodDef.static)
        bodyBindings = bodyBindings.extend("this", VariableBinding(owningType, false, 0))
    // Populate the bindings
    for (typedParam in typedParams)
        bodyBindings = bodyBindings.extend(bindings(typedParam, bodyBindings).first)
    // Get the return type of the method:
    val returnType = getTypeDef(methodDef.returnType, typeCache, currentTypeGenerics)
    // Type-check the method body to be the return type.
    val typedBody = checkExpr(methodDef.body, returnType, bodyBindings, typeCache, owningType, currentTypeGenerics).expr

    // Get its runtime name:
    val runtimeName = when {
        // If this is a class, and the name is "new", make it "<init>"
        owningType is TypeDef.ClassDef && methodDef.name == "new" -> "<init>"
        // If the disambiguation index is > 0, use it
        disambiguationIndex > 0 -> methodDef.name + "$" + disambiguationIndex
        // Default, just the name
        else -> methodDef.name
    }
    // And return the method def.
    return MethodDef.SnuggleMethodDef(
        methodDef.pub, methodDef.static, owningType, methodDef.name, returnType, paramTypes,
        runtimeName, methodDef.loc, typedBody
    )
}