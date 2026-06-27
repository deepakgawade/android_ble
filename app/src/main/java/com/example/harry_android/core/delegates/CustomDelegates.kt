package com.example.harry_android.core.delegates

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class LoggingDelegate<T>(initialValue:T): ReadWriteProperty<Any?, T> {
    private var value:T = initialValue

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        println("GET ${property.name} = $value")
        return  value
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        println("SET ${property.name}: ${this.value} -> $value")
        this.value = value
    }

}