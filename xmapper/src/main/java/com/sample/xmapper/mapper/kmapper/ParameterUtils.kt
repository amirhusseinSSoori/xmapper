package com.sample.xmapper.mapper.kmapper

import com.sample.xmapper.mapper.annotations.KConverter
import com.sample.xmapper.mapper.conversion.KConvertBy
import com.sample.xmapper.shared.*

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.staticFunctions
import kotlin.reflect.jvm.isAccessible

internal fun <T : Any> KClass<T>.getConverters(): Set<Pair<KClass<*>, KFunction<T>>> =
    convertersFromConstructors(this) + convertersFromStaticMethods(this) + convertersFromCompanionObject(this)

private fun <T> Collection<KFunction<T>>.getConvertersFromFunctions(): Set<Pair<KClass<*>, KFunction<T>>> {
    return this.getAnnotatedFunctions<KConverter, T>()
        .map { func ->
            func.isAccessible = true

            func.parameters.single().getKClass() to func
        }.toSet()
}

private fun <T : Any> convertersFromConstructors(clazz: KClass<T>): Set<Pair<KClass<*>, KFunction<T>>> {
    return clazz.constructors.getConvertersFromFunctions()
}

@Suppress("UNCHECKED_CAST")
private fun <T : Any> convertersFromStaticMethods(clazz: KClass<T>): Set<Pair<KClass<*>, KFunction<T>>> {
    val staticFunctions: Collection<KFunction<T>> = clazz.staticFunctions as Collection<KFunction<T>>

    return staticFunctions.getConvertersFromFunctions()
}

@Suppress("UNCHECKED_CAST")
private fun <T : Any> convertersFromCompanionObject(clazz: KClass<T>): Set<Pair<KClass<*>, KFunction<T>>> {
    return clazz.getAnnotatedFunctionsFromCompanionObject<KConverter>()?.let { (instance, functions) ->
        functions.map { function ->
            val func: KFunction<T> = KFunctionWithInstance(function, instance) as KFunction<T>

            func.parameters.single().getKClass() to func
        }.toSet()
    } ?: emptySet()
}

@Suppress("UNCHECKED_CAST")
internal fun <T : Any> ValueParameter<T>.getConverters(): Set<Pair<KClass<*>, KFunction<*>>> {
    return annotations.mapNotNull { paramAnnotation ->
        paramAnnotation.annotationClass
            .findAnnotation<KConvertBy>()
            ?.converters
            ?.map { it.primaryConstructor!!.call(paramAnnotation) }
    }.flatten().map { (it.srcClass) to it::convert as KFunction<*> }.toSet()
}

// ???????????????converter??????????????????????????????converter?????????
internal fun <T : Any> Set<Pair<KClass<*>, KFunction<T>>>.getConverter(input: KClass<out T>): KFunction<T>? =
    this.find { (key, _) -> input.isSubclassOf(key) }?.second

// ??????????????????????????????KMapper??????????????????????????????????????????1????????????????????????????????????????????????????????????2??????????????????????????????????????????
internal val PARAMETER_DUMMY = "" to null
