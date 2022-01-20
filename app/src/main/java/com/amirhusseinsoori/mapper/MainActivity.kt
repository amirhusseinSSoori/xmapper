package com.amirhusseinsoori.mapper

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.sample.xmapper.mapper.kmapper.*





class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        data class InnerDst(val foo: List<Int>, val bar: Int)
        data class Dst(val param: InnerDst)

        data class InnerSrc(val foo: List<Int>, val bar: Int)
        data class Src(val param: InnerSrc)


        val src = Src(InnerSrc(listOf(8,9), 2))
        val dst = KMapper(::Dst).map(src)


        Log.e("TAG", "onCreate: ${dst.param}", )








    }

}

data class Dst(
    val foo: String,
    val bar: String,
    val  baz: Int?,



    )

data class Dst2(
    val foo: String,
    val bar: String,
    val  baz: Int?,



    )