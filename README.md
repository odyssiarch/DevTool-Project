# JarTool (kerangka)

app/src/main/java/com/jartool/app
- MainActivity.kt            titik masuk
- ui/AppRoot.kt              navigasi bawah: Alat, Proyek, Setelan
- ui/theme/                  warna dan tema biru kehitaman
- ui/screens/                Alat, Proyek, Setelan, placeholder detail alat
- core/DexVersion.kt         baca versi dex dari header (035, 037, 038, 039, ...)
- core/JarPackager.kt        ekstrak classes*.dex dari JAR, kemas ulang JAR
- core/DexEngine.kt          antarmuka jadx, baksmali, smali (belum diisi)

Alur inti: JAR -> extractDex -> disassemble -> edit Smali -> assemble -> repack -> JAR baru
