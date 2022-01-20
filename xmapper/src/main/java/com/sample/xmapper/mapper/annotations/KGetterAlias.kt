package com.sample.xmapper.mapper.annotations

@Target(AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class KGetterAlias(val value: String)
