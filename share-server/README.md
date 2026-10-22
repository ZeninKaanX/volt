# Volt paylaşım sunucusu · alpha

Python 3.11+ ve standart kütüphane ile çalışan başlangıç hizmeti. Klip yükleme, tarayıcıda video sayfası, HTTP Range ile oynatma, süre dolumu ve sahibinin silmesi desteklenir. Bu kod projede hazırlanmıştır; internete dağıtılmamıştır.

## Yerel deneme

Rastgele bir yükleme anahtarı oluştur ve **kendi ortamında** `VOLT_UPLOAD_TOKEN` değişkenine kaydet. Örneğin anahtar üretmek için `python3 -c 'import secrets; print(secrets.token_urlsafe(32))'` kullanılabilir. Bu anahtarı Discord/Minecraft token'larıyla karıştırma.

```sh
export VOLT_UPLOAD_TOKEN='kendi-uzun-rastgele-anahtarin'
export VOLT_PUBLIC_URL='http://127.0.0.1:8765'
python3 share-server/server.py
```

Volt ayarlarında yükleme adresi: `http://127.0.0.1:8765/api/clips`. Yükleme anahtarı sunucudakiyle aynı olmalıdır. Bu yerel adres, başka bilgisayarlardaki arkadaşların için çalışmaz.

## İnternetten erişim

Sunucuyu kendi hosting sisteminde HTTPS reverse proxy arkasında çalıştır. Örnek Caddy yapılandırması:

```caddy
clips.example.com {
    request_body {
        max_size 250MB
    }
    reverse_proxy 127.0.0.1:8765
}
```

`VOLT_PUBLIC_URL=https://clips.example.com` olarak ayarlanır; modun adresi `https://clips.example.com/api/clips` olur. Gerçek alan adı, TLS ve hosting kullanıcı tarafından sağlanır. Video bağlantısına sahip olan herkes videoyu izleyebilir; bağlantılar bir giriş hesabı ile korunmaz.

Hizmet varsayılan yalnızca `127.0.0.1:8765` üzerinde dinler. `VOLT_BIND`, `VOLT_PORT`, `VOLT_STORAGE` ortam değişkenleriyle değiştirilebilir. Üretimde bağımsız bir servis hesabı, kalıcı disk, yedekleme, reverse proxy bağlantı sınırları ve süreç yöneticisi kullan.

## Limitler ve kimlik

- Klip başına 250 MiB, toplam 10 GiB.
- 14 gün süre; süre dolunca erişim kapanır ve dosya temizlenir.
- Aynı anda en fazla 2 yükleme, anahtar başına dakikada 6 istek.
- Yükleme parçalar halinde diske akar; bütün video RAM'e alınmaz.
- MP4 başlığı, boyut, yetki ve sahiplik kontrolü vardır.
- Birden fazla bağımsız anahtar `VOLT_UPLOAD_TOKEN` içinde virgülle ayrılabilir. Dosya sahibi anahtarın özetiyle belirlenir; anahtarın kendisi veritabanına yazılmaz.

Bu alpha bir herkese açık kayıt/hesap sistemi değildir. Minecraft nicki kimlik doğrulama sayılmaz. Yaygın dağıtım için kullanıcı başına anahtar sağlama/iptal etme, depolama kotası, kötüye kullanım ve destek süreci ayrıca gerekir. Tek bir ortak yükleme anahtarını mod JAR'ına gömme.

## API

- `PUT /api/clips`: `Authorization: Bearer …`, `Content-Type: video/mp4`, `Content-Length`. Yanıt `201 {"id":"…","url":"…/c/…","expiresAt":…}`.
- `GET /c/{id}`: markalı video sayfası.
- `GET /media/{id}.mp4`: `Range` destekli video.
- `DELETE /api/clips/{id}`: sahibinin yükleme anahtarı ile silme.
- `GET /health`: sağlık kontrolü.

```sh
python3 -m unittest discover -s share-server -v
```
