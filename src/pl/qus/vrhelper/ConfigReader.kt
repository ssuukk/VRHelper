package pl.qus.vrhelper

import java.io.*

class Config (
    var server : String = "localhost",
    var port : Int = 80,
    var dirs : Array<File> = arrayOf(File("/"))
)

fun readConfig() : Config {
    val conf = Config()

    BufferedReader(InputStreamReader(FileInputStream("config.txt"))).forEachLine {
        val tokeny = it.split("=").map(String::trim)

        if(tokeny.isNotEmpty()) {
            if (tokeny[0] == "server") {
                conf.server = try {
                    tokeny[1]
                } catch (ex: Exception) {
                    "localhost"
                }
            } else if (tokeny[0] == "port") {
                conf.port = try {
                    Integer.parseInt(tokeny[1])
                } catch (ex: Exception) {
                    80
                }
            } else if (tokeny[0] == "dirs" && tokeny.size>1) {
                val dirList = tokeny[1].split(";").map(String::trim)
                conf.dirs = Array(dirList.size,{File(dirList[it])})
            }
        }
    }

    return conf
}