package representation.passes.verify_generics

import errors.CompilationException
import representation.asts.resolved.*
import representation.passes.lexing.Loc
import util.caching.EqualityIncrementalCalculator

private typealias VerifyCalc = EqualityIncrementalCalculator<ResolvedTypeDef, Unit>

/**
 * Verifies that generic counts are correct.
 * For example, the type List<T> has one generic.
 * This prevents you from using it with 0 generics
 * or 2+ generics.
 */
fun verify(ast: ResolvedAST) {
    val verifyCalc = VerifyCalc()
    // Verify code in all files
    ast.allFiles.values.forEach { verifyExpr(it.code, verifyCalc) }
}

private fun verifyExprs(calc: VerifyCalc, vararg exprs: ResolvedExpr) = exprs.forEach { verifyExpr(it, calc) }

private fun verifyExpr(expr: ResolvedExpr, calc: VerifyCalc): Unit = when (expr) {
    is ResolvedExpr.Import -> {}
    is ResolvedExpr.Block -> expr.exprs.forEach { verifyExpr(it, calc) }
    is ResolvedExpr.Declaration -> {
        verifyPattern(expr.pattern, calc)
        verifyExpr(expr.initializer, calc)
    }
    is ResolvedExpr.Assignment -> verifyExprs(calc, expr.lhs, expr.rhs)
    is ResolvedExpr.Return -> verifyExpr(expr.rhs, calc)
    is ResolvedExpr.If -> {
        verifyExprs(calc, expr.cond, expr.ifTrue)
        expr.ifFalse?.let { verifyExpr(it, calc) } ?: Unit
    }
    is ResolvedExpr.While -> verifyExprs(calc, expr.cond, expr.body)
    is ResolvedExpr.For -> verifyExprs(calc, expr.iterable, expr.body)
    is ResolvedExpr.Literal -> {}
    is ResolvedExpr.Variable -> {}
    is ResolvedExpr.Tuple -> expr.elements.forEach { verifyExpr(it, calc) }
    is ResolvedExpr.Lambda -> {
        expr.params.forEach { verifyPattern(it, calc) }
        verifyExpr(expr.body, calc)
    }
    is ResolvedExpr.FieldAccess -> verifyExpr(expr.receiver, calc)
    is ResolvedExpr.StaticFieldAccess -> {}
    is ResolvedExpr.MethodCall -> {
        expr.genericArgs.forEach { verifyType(it, calc) }
        verifyExpr(expr.receiver, calc)
        expr.args.forEach { verifyExpr(it, calc) }
    }
    is ResolvedExpr.StaticMethodCall -> {
        verifyType(expr.receiverType, calc)
        expr.genericArgs.forEach { verifyType(it, calc) }
        expr.args.forEach { verifyExpr(it, calc) }
    }
    is ResolvedExpr.SuperMethodCall -> {
        expr.genericArgs.forEach { verifyType(it, calc) }
        expr.args.forEach { verifyExpr(it, calc) }
    }
    is ResolvedExpr.ConstructorCall -> {
        expr.type?.let { verifyType(it, calc) }
        expr.args.forEach { verifyExpr(it, calc) }
    }
    is ResolvedExpr.RawStructConstructor -> {
        expr.type?.let { verifyType(it, calc) }
        expr.fieldValues.forEach { verifyExpr(it, calc) }
    }
}

private fun verifyPattern(pattern: ResolvedInfalliblePattern, calc: VerifyCalc): Unit = when (pattern) {
    is ResolvedInfalliblePattern.Empty ->
        pattern.typeAnnotation?.let { verifyType(it, calc) } ?: Unit
    is ResolvedInfalliblePattern.Binding ->
        pattern.typeAnnotation?.let { verifyType(it, calc) } ?: Unit
    is ResolvedInfalliblePattern.Tuple ->
        pattern.elements.forEach { verifyPattern(it, calc) }
}

private fun verifyType(type: ResolvedType, calc: VerifyCalc): Unit = when (type) {
    is ResolvedType.Basic -> {
        if (type.base.numGenerics != type.generics.size)
            throw GenericCountException(type.base, type.generics.size, type.loc)
        verifyTypeDef(type.base, calc)
        type.generics.forEach { verifyType(it, calc) }
    }
    is ResolvedType.Tuple -> type.elements.forEach { verifyType(it, calc) }
    is ResolvedType.Func -> {
        type.paramTypes.forEach { verifyType(it, calc) }
        verifyType(type.returnType, calc)
    }
    is ResolvedType.TypeGeneric, is ResolvedType.MethodGeneric -> {}
}

private fun verifyTypeDef(typeDef: ResolvedTypeDef, calc: VerifyCalc): Unit = when (typeDef) {
    is ResolvedTypeDef.Builtin -> {} // Assumed correct
    is ResolvedTypeDef.Class -> calc.compute(typeDef) {
        verifyType(it.superType, calc)
        it.fields.forEach { verifyType(it.annotatedType, calc) }
        it.methods.forEach {
            it.params.forEach { verifyPattern(it, calc) }
            verifyType(it.returnType, calc)
            verifyExpr(it.body, calc)
        }
    }
    is ResolvedTypeDef.Indirection -> verifyTypeDef(typeDef.promise.expect(), calc) // Recurse
    is ResolvedTypeDef.Struct -> calc.compute(typeDef) {
        it.fields.forEach { verifyType(it.annotatedType, calc) }
        it.methods.forEach {
            it.params.forEach { verifyPattern(it, calc) }
            verifyType(it.returnType, calc)
            verifyExpr(it.body, calc)
        }
    }
}

class GenericCountException(type: ResolvedTypeDef, givenGenericCount: Int, loc: Loc)
    : CompilationException("Attempt to instantiate type ${type.name} with $givenGenericCount generics, but it expected ${type.numGenerics}", loc)