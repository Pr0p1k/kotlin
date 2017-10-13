package org.jetbrains.kotlin.j2k.tree.visitors

import org.jetbrains.kotlin.j2k.tree.*

interface JKTransformer<in D> {
    fun <E: JKElement> transformElement(element: JKElement, data: D): E 
    fun <E: JKClass> transformClass(klass: JKClass, data: D): E = transformDeclaration(klass, data)
    fun <E: JKStatement> transformStatement(statement: JKStatement, data: D): E = transformElement(statement, data)
    fun <E: JKExpression> transformExpression(expression: JKExpression, data: D): E = transformStatement(expression, data)
    fun <E: JKLoop> transformLoop(loop: JKLoop, data: D): E = transformStatement(loop, data)
    fun <E: JKDeclaration> transformDeclaration(declaration: JKDeclaration, data: D): E = transformElement(declaration, data)
    fun <E: JKBlock> transformBlock(block: JKBlock, data: D): E = transformElement(block, data)
    fun <E: JKCall> transformCall(call: JKCall, data: D): E = transformExpression(call, data)
    fun <E: JKIdentifier> transformIdentifier(identifier: JKIdentifier, data: D): E = transformElement(identifier, data)
    fun <E: JKTypeIdentifier> transformTypeIdentifier(typeIdentifier: JKTypeIdentifier, data: D): E = transformIdentifier(typeIdentifier, data)
    fun <E: JKNameIdentifier> transformNameIdentifier(nameIdentifier: JKNameIdentifier, data: D): E = transformIdentifier(nameIdentifier, data)
    fun <E: JKLiteralExpression> transformLiteralExpression(literalExpression: JKLiteralExpression, data: D): E = transformExpression(literalExpression, data)
    fun <E: JKModifierList> transformModifierList(modifierList: JKModifierList, data: D): E = transformElement(modifierList, data)
    fun <E: JKModifier> transformModifier(modifier: JKModifier, data: D): E = transformElement(modifier, data)
    fun <E: JKAccessModifier> transformAccessModifier(accessModifier: JKAccessModifier, data: D): E = transformModifier(accessModifier, data)
    fun <E: JKDeclaration> transformJavaField(javaField: JKJavaField, data: D): E = transformDeclaration(javaField, data)
    fun <E: JKDeclaration> transformJavaMethod(javaMethod: JKJavaMethod, data: D): E = transformDeclaration(javaMethod, data)
    fun <E: JKLoop> transformJavaForLoop(javaForLoop: JKJavaForLoop, data: D): E = transformLoop(javaForLoop, data)
    fun <E: JKExpression> transformJavaAssignmentExpression(javaAssignmentExpression: JKJavaAssignmentExpression, data: D): E = transformExpression(javaAssignmentExpression, data)
    fun <E: JKCall> transformJavaCall(javaCall: JKJavaCall, data: D): E = transformCall(javaCall, data)
    fun <E: JKTypeIdentifier> transformJavaTypeIdentifier(javaTypeIdentifier: JKJavaTypeIdentifier, data: D): E = transformTypeIdentifier(javaTypeIdentifier, data)
    fun <E: JKLiteralExpression> transformJavaStringLiteralExpression(javaStringLiteralExpression: JKJavaStringLiteralExpression, data: D): E = transformLiteralExpression(javaStringLiteralExpression, data)
    fun <E: JKAccessModifier> transformJavaAccessModifier(javaAccessModifier: JKJavaAccessModifier, data: D): E = transformAccessModifier(javaAccessModifier, data)
    fun <E: JKDeclaration> transformKtFun(ktFun: JKKtFun, data: D): E = transformDeclaration(ktFun, data)
    fun <E: JKDeclaration> transformKtConstructor(ktConstructor: JKKtConstructor, data: D): E = transformDeclaration(ktConstructor, data)
    fun <E: JKDeclaration> transformKtPrimaryConstructor(ktPrimaryConstructor: JKKtPrimaryConstructor, data: D): E = transformKtConstructor(ktPrimaryConstructor, data)
    fun <E: JKStatement> transformKtAssignmentStatement(ktAssignmentStatement: JKKtAssignmentStatement, data: D): E = transformStatement(ktAssignmentStatement, data)
    fun <E: JKCall> transformKtCall(ktCall: JKKtCall, data: D): E = transformCall(ktCall, data)
}