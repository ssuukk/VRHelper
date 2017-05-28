package pl.qus.vrhelper

import java.io.File

fun guessVideoType(path : String) : String {
    val lower = path.toLowerCase()

    val types = arrayOf(
    "cylinder_slice_2x25_3dv",
    "cylinder_slice_16x9_3dv",
    "180x180_squished_3dh",
    "180-hemispheres",
    "180hemispheres",
    "_planetarium",
    "_icosahedron",
    "_cubemap_tb",
    "_cubemap_lr​",
    "_octahedron",
    "180x180_3dv",
    "180x180_3dh",
    "180x160_3dv",
    "180x101_3dh",
    "_fulldome",
    "_mono360",
    "_cubemap",
    "180x180",
    "180x101",
    "sib3d",
    "_v360",
    "_rtxp",
    "_3dpv",
    "_3dph",
    "3dv",
    "_tb",
    "3dh",
    "_lr"
    )


    // vrdesktophd - dobrac projekcje

    val gearVrRegex = """.*gear.*vr""".toRegex()
    val containsGearVr = gearVrRegex.matchEntire(lower) != null

    return types.firstOrNull { lower.contains(it) } ?: if(containsGearVr) {
        "180x180_3dh"
    } else {
        val sbs = lower.contains("sbs")
        val tb = lower.contains("ou") || lower.contains("tb")
        val threeDee = lower.contains("3d")

        if (lower.contains("oculus")) "180x180_3dh"
        else if(lower.contains("2880x1440")) "180x180_3dh"
        else if(lower.contains("vr")) "180x180_3dh"
        else if(threeDee && sbs) "_3dph"
        else if(threeDee && tb) "_3dpv"
        else "_2dp"
    }
}

fun guessAudioType(path : String) : String {
    return if(path.toLowerCase().contains("ac3") || path.toLowerCase().contains("dts") || path.contains("5.1")) "_5.1"
    else ""
}

//audio:
//
//Mono	N/A
//Stereo	N/A
//Ambisonic	N/A
//5.1 Spatial	"_5.1"
//Quadraphonic	"quadraphonic"
//Binaural	"_binaural"
//Mach1 Spatial	"_m1spatial"
//

fun findThumbnil(path : String) : String? {
    val asFile = File(path)
    val name = asFile.name.dropLast(asFile.extension.length+1)

    return asFile.parentFile.listFiles().firstOrNull{
        val ext = it.extension
        it.name.contains(name) && (ext == "jpg" || ext == "png" || ext == "gif" || ext == "jpeg")
    }?.path
}

fun isVideo(f: File): Boolean {
    val ext = f.extension
    return ext == "avi" || ext == "mp4" || ext == "mkv" || ext == "ogv" || ext == "mk3d" || ext == "wmv" || ext == "flv" || ext == "mov" || ext == "mpg" || ext == "mpeg"
}