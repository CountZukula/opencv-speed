package model

fun<T> printTime(s:String,function:()->T):T{
    val start=System.currentTimeMillis()
    val result=function.invoke()
    val end=System.currentTimeMillis()
    println("$s: ${end - start}ms")
    return result
}


