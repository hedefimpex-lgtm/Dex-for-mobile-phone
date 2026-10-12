# DeZ — telefonlar için masaüstü (DeX benzeri)

DeZ, Samsung DeX'teki masaüstü deneyimini herhangi bir Android telefona getiren bir başlatıcı (launcher) uygulamasıdır.
Telefon ekranında veya USB-C/HDMI ile bağlanan harici bir monitörde çalışır.

## Özellikler

**Pencere yapısı (DeX ile aynı düzen)**
- Başlık çubuğu: uygulama simgesi, başlık, **Küçült / Ekranı kapla–Önceki boyut / Kapat** düğmeleri
- Başlık çubuğundan sürükleyerek taşıma, çift dokunarak büyütme
- Kenar ve köşelerden yeniden boyutlandırma
- Ekranın sol/sağ kenarına sürükleyince yarım ekrana yaslama, üst kenara sürükleyince tam ekran (canlı önizleme ile)
- Büyütülmüş pencereyi sürükleyince parmağın altında eski boyutuna dönme
- Odak, z-sırası ve basamaklı (cascade) yerleşim; telefon ekranında da pencereler çerçeveli, yüzen pencere olarak açılır (ekranın büyük kısmını kaplayarak)

**Masaüstü yapısı**
- Görev çubuğu: Uygulamalar, Son uygulamalar, Masaüstünü göster, Geri; sabitlenmiş ve çalışan uygulamalar
  (çalışan / odaktaki göstergesiyle); sistem tepsisi (ağ, pil, saat-tarih, bildirimler)
- Uygulama paneli: arama + tüm uygulamaların ızgarası; uzun basınca bağlam menüsü
  (pencerede aç, tam ekranda aç, yeni pencere, görev çubuğuna sabitle, masaüstüne ekle, uygulama bilgisi)
- Masaüstü simgeleri (yukarıdan aşağı, soldan sağa) ve masaüstü bağlam menüsü
- Son uygulamalar ekranı: her pencere için canlı küçük resim, kapat / tümünü kapat
- Duvar kağıdı seçenekleri (gradyanlar veya sistem duvar kağıdı)
- Klavye kısayolları: `Meta` uygulama paneli, `Meta+D` masaüstü, `Meta+E` dosyalar, `Meta+←/→` yasla,
  `Meta+↑` büyüt, `Meta+↓` küçült, `Alt+Tab` pencere değiştir, `Alt+F4` kapat

**Uygulamalar**
- Yerleşik pencereli uygulamalar: **Dosyalarım**, **Notlar**, **Hesap Makinesi**, **İnternet** (masaüstü site modu), **DeZ Ayarları**
- Telefondaki tüm Android uygulamaları (iş profili dahil). Cihaz serbest biçimli pencereleri destekliyorsa
  bu uygulamalar da DeX'teki gibi açılır pencerede (launch bounds ile) açılır; desteklemiyorsa tam ekranda açılır.

**Harici ekran**
- Monitör bağlanınca "DeZ'i bu ekranda başlat" bildirimi; Android 10+ üzerinde ikincil ekran ana ekranı
  (`SECONDARY_HOME`) olarak da kayıtlıdır.

## Android uygulamalarını pencerede açmak

Samsung dışındaki çoğu telefonda serbest biçimli pencereler varsayılan olarak kapalıdır. Etkinleştirmek için
Geliştirici seçeneklerinde **"Serbest biçimli pencereleri etkinleştir"** ve **"Etkinlikleri yeniden boyutlandırılabilir
olmaya zorla"** seçeneklerini açın veya:

```sh
adb shell settings put global enable_freeform_support 1
adb shell settings put global force_resizable_activities 1
adb reboot
```

DeZ'in kendi uygulamaları bu ayar olmadan da her zaman pencerede çalışır.

## Derleme

```sh
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Gereksinimler: JDK 17, Android SDK (compileSdk 34). minSdk 26 (Android 8.0).
Her push'ta GitHub Actions APK'yı derler ve "DeZ-debug-apk" adıyla yükler.

## Kurulum

1. APK'yı yükleyin ve **DeZ**'i açın.
2. Her açılışta masaüstü modunda başlamak için *DeZ Ayarları → Ana ekran → Varsayılan ana ekran uygulamasını seç*.
3. Normal başlatıcıya dönmek için Uygulamalar panelindeki **DeZ'den çık** düğmesini kullanın.

## Pencere çerçevesi (`WindowFrame`)

Her DeZ penceresi aynı basit çerçeveyle çizilir:

```
┌──────────────────────────────────────────┐
│ [simge] Başlık                ─   □   ✕  │  ← başlık çubuğu
├──────────────────────────────────────────┤
│                                          │
│                  içerik                  │
│                                          │
└──────────────────────────────────────────┘
```

- `wm/WindowFrame.java` yalnızca görünümü çizer: başlık çubuğu, Küçült / Ekranı kapla / Kapat düğmeleri,
  kenarlık ve içerik alanı. Düğme basışlarını `WindowFrame.Listener` ile bildirir.
- `wm/DesktopWindow.java` bu çerçeveyi kullanır ve davranışı ekler: sürükleme, boyutlandırma, yaslama.

Yeni bir pencereli uygulama yazmak için yalnızca içerik görünümünü vermek yeterlidir; örnek:
`apps/SampleApp.java` (**Örnek Pencere**). Sınıfı kopyalayın, `createView` içinde kendi görünümünüzü
döndürün ve `apps/BuiltInApps.java` listesine ekleyin.

## Proje yapısı

```
app/src/main/java/com/dez/desktop/
  DesktopActivity.java        masaüstü kabuğu (her şeyi birleştirir)
  SecondaryDesktopActivity    harici ekran masaüstü
  wm/WindowFrame.java         basit pencere çerçevesi: başlık çubuğu, düğmeler, içerik alanı
  wm/DesktopWindow.java       pencere davranışı: sürükleme, boyutlandırma
  wm/WindowLayer.java         pencere yöneticisi: odak, küçült/büyüt, yaslama
  shell/Taskbar.java          görev çubuğu + sistem tepsisi
  shell/StartMenu.java        uygulama paneli
  shell/RecentsOverlay.java   son uygulamalar
  shell/DesktopGrid.java      masaüstü simgeleri
  apps/                       yerleşik pencereli uygulamalar
  ExternalLauncher.java       Android uygulamalarını (serbest) pencerede başlatma
```

Not: DeZ, Samsung DeX'in kodunu içermez; aynı pencere ve uygulama düzenini bağımsız olarak uygular.
