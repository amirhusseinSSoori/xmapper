package com.sample.xmapper.shared

import kotlin.reflect.KClass

interface ValueParameter<T : Any> {
    val annotations: List<Annotation>
    val isNullable: Boolean
    val isOptional: Boolean
    val name: String
    val requiredClazz: KClass<T>
}