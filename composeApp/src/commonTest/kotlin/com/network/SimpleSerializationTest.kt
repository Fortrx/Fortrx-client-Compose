package com.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
data class TestData(val name: String)

class SimpleSerializationTest {
    @Test
    fun testSerialization() {
        val data = TestData("Hello")
        val json = Json.encodeToString(TestData.serializer(), data)
        println("JSON: $json")
        val decoded = Json.decodeFromString(TestData.serializer(), json)
        assertEquals(data, decoded)
    }
}
