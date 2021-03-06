package com.sample.xmapper.shared.internal



import com.sample.xmapper.shared.ArgumentAdaptor
import kotlin.reflect.KParameter

internal class BucketGenerator(
    private val parameters: List<KParameter>,
    private val binders: List<ArgumentBinder>,
    instance: Any?
) {
    private val originalValueArray: Array<Any?> = arrayOfNulls(parameters.size)
    private val originalInitializationStatus: BooleanArray = BooleanArray(parameters.size)

    init {
        if (instance != null) {
            originalValueArray[0] = instance
            originalInitializationStatus[0] = true
        }
    }

    fun generate(adaptor: ArgumentAdaptor): ArgumentBucket =
        ArgumentBucket(parameters, originalValueArray.clone(), originalInitializationStatus.clone(), binders, adaptor)
}