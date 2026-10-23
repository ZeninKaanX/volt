# Volt 0.6 ölçümleri

Ortam: Intel UHD Graphics CML GT2, Mesa 26.2.5, Java 21, Minecraft 1.21.11 Fabric, kullanıcı mod paketinin kopyası, Iris + LSM 0.7, 1920×1080 tam ekran. Ayrı test dünyası; asıl dünyalara dokunulmadı. Masaüstünde diğer uygulamalar açıktı; çevresel yük tamamen sabit değildi.

Ölçüm önce kayıt kapalı, sonra kayıt açık, sonra tekrar kapalı olarak aynı sabit test sahnesinde yapıldı. AFK kare sınırı ve test penceresinin otomatik simge durumuna geçmesi test istemcisinde kapatıldı. Önceki odak kayıplı 10 FPS denemesi geçersiz sayıldı.

## 1080p / 30 FPS — aynı kayıt çözünürlüğü

| Sürüm | Önce kapalı ort. ms | Kayıt açık ort. ms | Açık p95 ms | Sonra kapalı ort. ms |
|---|---:|---:|---:|---:|
| 0.5.1 | 7.432 | 12.723 | 21.289 | 7.452 |
| 0.6 NV12 | 9.665 | 10.257 | 15.941 | 6.317 |

Ham değerler `controlled-before-1080p.txt` ve `controlled-after-1080p.txt` içindedir. Kayıt açıkken 0.5.1 yaklaşık 79 FPS, 0.6 yaklaşık 98 FPS ölçtü. Kayıt kapalı başlangıç/bitiş değerlerindeki oynama arka plan yükünün değiştiğini gösterir; bu tek ölçüm kesin bir yüzde kazancı veya bütün sistemlerde aynı iyileşmeyi kanıtlamaz.

## Oyun önceliği — 720p / 30 FPS

Donanım VAAPI, gerçek 1280×720 NV12 kaydı ve MP4 dışa aktarma çalıştı. Yakalama çağrısının üstel hareketli ortalaması yaklaşık 0.67 ms, kaçırılan PBO slotu 0 ölçüldü (`protected-720p-recording-result.txt`). Son kapalı faz tamamlanmadan test işlemi SIGKILL ile sonlandı; bu denemeden tam karşılaştırmalı FPS sonucu verilmedi.

## Doğrulama

NV12 yoluyla üretilen gerçek MP4 decode edildi: görüntü doğru yönlü, renkli, limited-range BT.709; ilk shader denemesinde görülen siyah örnekleme, Minecraft/Iris sampler durumunu izole edip geri yükleyerek giderildi. `nv12-decoded-frame.png` son düzeltilmiş gerçek videodan alınmıştır.

Aynı çözünürlükte GPU→CPU kare yükü 4 B/piksel yerine 1.5 B/piksel (%62.5 azalma). 1080p RGBA’dan 720p NV12’ye geçişte ham aktarım yaklaşık %83.3 azalır; bu FPS artış oranı değildir. Oyun önceliği kayıt kalitesini sınırlayan açık bir tercihtir. Oyunun kendi çözünürlüğü veya FPS limiti kurulum sırasında değiştirilmez.

Üç tekrar kullanılan kare tamponu korunur. Sürücü desteklediğinde persistent PBO, aksi halde normal PBO; en yeni hazır kare kopyalanır. Renk dönüşümü GPU’da, FFmpeg filtre iş parçacıkları sınırlı. Menü kapanınca bekleyen thumbnail işleri geçersizleştirilir; o anda başlamış tek kare çıkarma işlemi tamamlanabilir.
