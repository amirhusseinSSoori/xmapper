@file:Suppress("FunctionName")

package com.sample.xmapper.mapper.kmapper

import kotlin.reflect.KFunction
import com.sample.xmapper.mapper.kmapper.BoundKMapper as Bound
import com.sample.xmapper.mapper.kmapper.KMapper as Normal
import com.sample.xmapper.mapper.kmapper.PlainKMapper as Plain

inline fun <reified S : Any, reified D : Any> BoundKMapper(): Bound<S, D> = Bound(D::class, S::class)

inline fun <reified S : Any, reified D : Any> BoundKMapper(
    noinline parameterNameConverter: ((String) -> String)
): Bound<S, D> = Bound(D::class, S::class, parameterNameConverter)

inline fun <reified S : Any, D : Any> BoundKMapper(function: KFunction<D>): Bound<S, D> = Bound(function, S::class)

inline fun <reified S : Any, D : Any> BoundKMapper(
    function: KFunction<D>,
    noinline parameterNameConverter: ((String) -> String)
): Bound<S, D> = Bound(function, S::class, parameterNameConverter)

private inline fun <reified T : Any> KMapper(): Normal<T> = Normal(T::class)

private inline fun <reified T : Any> KMapper(noinline parameterNameConverter: ((String) -> String)): Normal<T> =
    Normal(T::class, parameterNameConverter)

inline fun <reified T : Any> PlainKMapper(): Plain<T> = Plain(T::class)

inline fun <reified T : Any> PlainKMapper(noinline parameterNameConverter: ((String) -> String)): Plain<T> =
    Plain(T::class, parameterNameConverter)
