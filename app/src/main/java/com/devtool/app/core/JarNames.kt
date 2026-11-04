package com.devtool.app.core

/** Nama JAR untuk ditampilkan ke pengguna. */
object JarNames {
    /** Tanpa garis bawah dan selalu berakhiran .jar. Contoh: services_2 menjadi services2.jar. */
    fun display(project: String): String {
        val bersih = project.replace("_", "")
        return if (bersih.endsWith(".jar", ignoreCase = true)) bersih else "$bersih.jar"
    }
}
