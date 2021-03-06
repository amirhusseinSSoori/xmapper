package com.sample.xmapper.shared

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.functions

inline fun <reified A : Annotation> KClass<*>.getAnnotatedFunctionsFromCompanionObject(): Pair<Any, List<KFunction<*>>>? {
    return this.companionObject?.let { companionObject ->
        val temp = companionObject.functions.filter { function -> function.annotations.any { it is A } }

        if (temp.isEmpty()) {
            // 空ならその後の処理をしてもしょうがないのでnullに合わせる
            null
        } else {
            companionObject.objectInstance!! to temp
        }
    }
}

inline fun <reified A : Annotation, T> Collection<KFunction<T>>.getAnnotatedFunctions(): List<KFunction<T>> {
    return filter { function -> function.annotations.any { it is A } }
}

fun KParameter.getKClass(): KClass<*> = type.classifier as KClass<*>