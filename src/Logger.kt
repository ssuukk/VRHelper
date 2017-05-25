/**
 * Created by szczpr on 2017-05-24.
 */
package pl.qus.vrhelper

class Logger {
    companion object {
        fun d(s : String) {
            println(s)
        }

        fun d(s : String, t : Throwable) {
            print(s)
            println(t.stackTrace)
        }
    }
}