@file:DependsOn("org.json:json:20240303")

import org.json.JSONArray
import java.io.File

fun main(args: Array<String>) {
    println("=".repeat(60))
    println("TEST SCRIPT")
    println("=".repeat(60))
    println("Working directory: ${System.getProperty("user.dir")}")
    println("Arguments: ${args.joinToString()}")
    
    val testDir = File("app/src/main/assets/common/dictionaries")
    println("Dictionaries dir exists: ${testDir.exists()}")
    println("Dictionaries dir path: ${testDir.absolutePath}")
    
    if (testDir.exists()) {
        val files = testDir.listFiles { _, name -> name.endsWith("_base.json") }
        println("Found ${files?.size ?: 0} JSON files")
        files?.take(3)?.forEach { println("  - ${it.name}") }
    }
    
    println("=".repeat(60))
}

