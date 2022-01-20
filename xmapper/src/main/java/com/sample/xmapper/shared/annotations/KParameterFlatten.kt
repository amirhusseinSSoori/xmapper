package com.sample.xmapper.shared.annotations


import com.sample.xmapper.shared.NameJoiner
import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class KParameterFlatten(
    val fieldNameToPrefix: Boolean = true,
    val nameJoiner: KClass<out NameJoiner> = NameJoiner.Camel::class
)