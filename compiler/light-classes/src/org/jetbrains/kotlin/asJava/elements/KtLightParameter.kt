/*
 * Copyright 2000-2017 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.asJava.elements

import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.asJava.classes.lazyPub
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration

class KtLightParameter(
    private val dummyDelegate: PsiParameter,
    private val clsDelegateProvider: () -> PsiParameter?,
    private val index: Int,
    method: KtLightMethod
) : LightParameter(dummyDelegate.name ?: "p$index", dummyDelegate.type, method, KotlinLanguage.INSTANCE),
    KtLightDeclaration<KtParameter, PsiParameter> {

    private val lazyDelegate by lazyPub { clsDelegateProvider() ?: dummyDelegate }

    override val clsDelegate: PsiParameter get() = lazyDelegate

    override fun getType(): PsiType = lazyDelegate.type

    override fun getName(): String = dummyDelegate.name ?: lazyDelegate.name ?: super.getName()

    private val lightModifierList by lazyPub { KtLightSimpleModifierList(this, emptySet()) }

    private var lightIdentifier: KtLightIdentifier? = null

    override val kotlinOrigin: KtParameter?
        get() {
            val declaration = method.kotlinOrigin ?: return null

            val jetIndex = if (declaration.isExtensionDeclaration()) index - 1 else index
            if (jetIndex < 0) return null

            if (declaration is KtFunction) {
                val paramList = method.lightMemberOrigin?.parametersForJvmOverloads ?: declaration.valueParameters
                return if (jetIndex < paramList.size) paramList[jetIndex] else null
            }

            if (jetIndex != 0) return null

            val setter = when (declaration) {
                is KtPropertyAccessor -> if (declaration.isSetter) declaration else null
                is KtProperty -> declaration.setter
                is KtParameter -> return declaration
                else -> return null
            }

            return setter?.parameter
        }

    override fun getModifierList(): PsiModifierList = lightModifierList

    override fun getNavigationElement(): PsiElement = kotlinOrigin ?: super.getNavigationElement()

    override fun isValid(): Boolean = method.isValid

    @Throws(IncorrectOperationException::class)
    override fun setName(@NonNls name: String): PsiElement {
        kotlinOrigin?.setName(name)
        return this
    }

    override fun getContainingFile(): PsiFile = method.containingFile

    override fun getLanguage(): Language = KotlinLanguage.INSTANCE

    override fun getUseScope(): SearchScope {
        return kotlinOrigin?.useScope ?: LocalSearchScope(this)
    }

    override fun getText(): String = kotlinOrigin?.text ?: ""

    override fun getTextRange(): TextRange = kotlinOrigin?.textRange ?: TextRange.EMPTY_RANGE

    override fun getNameIdentifier(): PsiIdentifier? {
        if (lightIdentifier == null) {
            lightIdentifier = KtLightIdentifier(this, kotlinOrigin)
        }
        return lightIdentifier
    }

    override fun getParent(): PsiElement = method.parameterList

    override fun isEquivalentTo(another: PsiElement?): Boolean {
        if (this === another) return true

        return ApplicationManager.getApplication().runReadAction(Computable<Boolean> {
            if (another is KtParameter) {
                val kotlinOrigin = kotlinOrigin
                if (kotlinOrigin?.isEquivalentTo(another) == true) return@Computable true
            }

            if (another is KtLightParameter) {
                return@Computable kotlinOrigin != null && kotlinOrigin == another.kotlinOrigin && clsDelegate == another.clsDelegate
            }

            false
        })
    }

    override fun equals(other: Any?): Boolean {
        return other is PsiElement && isEquivalentTo(other)
    }

    override fun hashCode(): Int = kotlinOrigin?.hashCode() ?: 0
}
