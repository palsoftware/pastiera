import java.io.File

fun main(args: Array<String>) {
    val testFile = File("test-script-output.txt")
    testFile.writeText("Script executed successfully!\n")
    testFile.appendText("Working directory: ${System.getProperty("user.dir")}\n")
    testFile.appendText("Arguments: ${args.joinToString()}\n")
    
    val dictDir = File("app/src/main/assets/common/dictionaries")
    testFile.appendText("Dictionaries dir exists: ${dictDir.exists()}\n")
    
    if (dictDir.exists()) {
        val files = dictDir.listFiles { _, name -> name.endsWith("_base.json") }
        testFile.appendText("Found ${files?.size ?: 0} JSON files\n")
    }
    
    println("Test script completed. Check test-script-output.txt")
}

