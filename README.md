# DevTool

Alat Android untuk membongkar, mengedit, dan menyusun ulang JAR / DEX (Smali), dengan
dukungan dex 035 sampai 041, termasuk 2 dex dalam 1 kontainer. Aplikasi terpasang sebagai
`com.nva.devtool`, kode di paket `com.devtool.app`. Build lewat GitHub Actions
(`.github/workflows/build.yml`, `gradle assembleDebug`).

## Tampilan
Tema gelap hangat (arang + aksen oranye). Nav bawah model pil dengan 4 tab:
Beranda, Menu, Terminal, Setelan. Warna ada di `ui/theme/Color.kt` (`JarColors`),
komponen kartu bersama di `ui/components/Cards.kt`.

## Struktur (`app/src/main/java/com/devtool/app`)
- `MainActivity.kt`  titik masuk
- `ui/AppRoot.kt`, `ui/Routes.kt`  navigasi dan nama rute
- `ui/theme/`  warna dan tema
- `ui/components/`  kartu (`Cards.kt`), bagian umum (`Common.kt`), pohon paket,
  pemilih sumber, tombol aksi Smali
- `ui/screens/`  layar: Beranda, Menu, Terminal (`LiveLogcatScreen`), Setelan, Proyek,
  Edit JAR, Bandingkan JAR, Lihat Kode, Editor Smali, Logcat/Ramoops, Ekspor
- `ui/viewmodel/`  `CompareViewModel`, `SmaliJobViewModel`, `VmFactory`
- `core/`  `LogCapture` + `LogCaptureService` (tangkap logcat dan dmesg di latar belakang), mesin: `SmaliEngine`, `JarPackager`, `ContainerPacker`, `JarVerifier`,
  `JarComparer`, `SmaliDiff`, `JarAnalyzer`, `DexReader`, `Decompiler`, `Workspace`, dll.

## Alur inti
JAR -> bongkar dex -> Smali -> edit -> rakit ulang -> JAR baru (kompresi Stored seperti asli)
-> verifikasi hasil.
