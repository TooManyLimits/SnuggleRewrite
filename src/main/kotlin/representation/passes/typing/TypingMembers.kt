package representation.passes.typing

import representation.asts.resolved.ResolvedMethodDef
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedExpr
import representation.asts.typed.TypedPattern
import util.ConsList
import util.ConsMap
import util.caching.EqualityCache
import util.caching.EqualityMemoized
import util.extend

/**
 * Converting members of types, like methods/fields, from
 * ResolvedAST form to TypedAST form.
 */

/**
 * Convert a ResolvedMethodDef to a MethodDef.
 *
 * IMPORTANT NOTE: METHOD BODIES ARE TYPE CHECKED _LAZILY_!
 * IT IS _NOT_ CALLED AT THE SAME TIME AS THE METHODDEF IS CREATED!
 * This is because when type checking the method def's body, the def
 * may need access to properties of the owning type; however, this
 * leads to a reference loop. Somehow, some way, the method's *body*
 * needs to be initialized separately than the method's *signature*,
 * because the method can call itself.
 * This style of code has the potential to get very ugly, very fast.
 * That's what happened to the last project, and the horrendous code
 * spread like a wildfire through the codebase, because I didn't understand
 * how horrible it was until it was already too late. To minimize the impact
 * of this lazy body checking, the plan is to keep mutable values in the closure
 * to the _absolute minimum_. That means nothing except `owningType` will be
 * mutable in the closure, and the only mutability that `owningType` has is to
 * update its indirection value, once.
 * (typeCache is also mutable, but it only ever gains information, never loses it,
 * so this is okay.)
 *
 * - owningType: The type that this is a method of, so we know what type to use for "this"
 * - allMethodDefs: The list of all the type-resolved method defs on the owning type.
 *                  It's used to disambiguate method names from one another.
 * - methodDef: The method def that is to be type-checked.
 * - typeCache: The cache of TypeDefs that have been created while typing this AST.
 * - currentTypeGenerics: The generics of the owningType that we're currently instantiating with.
 */
fun typeMethod(owningType: TypeDef, allMethodDefs: List<ResolvedMethodDef>, methodDef: ResolvedMethodDef, typeCache: TypeDefCache, currentTypeGenerics: List<TypeDef>): MethodDef.SnuggleMethodDef {
    if (methodDef.numGenerics != 0) TODO()

    var disambiguationIndex = 0
    for (method in allMethodDefs) {
        if (method == methodDef) break;
        if (method.name == methodDef.name) disambiguationIndex++
    }

    // Get the param patterns and their types
    val paramPatterns = methodDef.params.map { inferPattern(it, typeCache, currentTypeGenerics) }
    val paramTypes = paramPatterns.map { it.type }
    // Get the return type
    val returnType = getTypeDef(methodDef.returnType, typeCache, currentTypeGenerics)
    // Create the lazy getter for the body
    val bodyGetter = lazy {
        // Get the bindings for the body, from the params
        var bodyBindings: ConsMap<String, VariableBinding> = ConsMap.of()
        // If non-static, add a "this" parameter as the first one in the bindings
        if (!methodDef.static)
            bodyBindings = bodyBindings.extend("this", VariableBinding(owningType, false, 0))
        // Populate the bindings
        for (typedParam in paramPatterns)
            bodyBindings = bodyBindings.extend(bindings(typedParam, bodyBindings).first)
        // Type-check the method body to be the return type
        val checkedBody = checkExpr(methodDef.body, returnType, bodyBindings, typeCache, returnType, owningType, currentTypeGenerics).expr
        // And get a Return() wrapper over it, since we want to return the body
        // Now lowerExpr() will handle all the stuff with returning plural types, etc.
        TypedExpr.Return(checkedBody.loc, checkedBody, checkedBody.type)
    }
    // Get the lazy runtime name:
    val runtimeNameGetter = lazy { when {
        // If this is a class and the name is "new", make runtime name be "<init>" instead
        (owningType.unwrap() is TypeDef.ClassDef) && methodDef.name == "new" -> "<init>"
        // If the disambiguation index is > 0, use it
        disambiguationIndex > 0 -> methodDef.name + "$" + disambiguationIndex
        // Default, just the name
        else -> methodDef.name
    }}

    // Return the method def
    return MethodDef.SnuggleMethodDef(
        methodDef.pub, methodDef.static, owningType, methodDef.name,
        returnType, paramTypes, runtimeNameGetter, methodDef.loc, bodyGetter
    )
}