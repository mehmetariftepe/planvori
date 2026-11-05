# Planvori — kişisel Google Drive kurulumu

Bu kurulum uygulamayı Play Store’da yayınlamaz. Planvori’yi mevcut APK gibi kullanmaya devam edebilirsin.

1. Telefon tarayıcısında https://console.cloud.google.com/ adresini kendi Google hesabınla aç. Gerekirse “Masaüstü sitesi” görünümünü kullan.
2. Proje seçiciden yeni bir proje oluştur: örneğin “Planvori Kisisel”.
3. APIs & Services → Library bölümünde “Google Drive API”yi bul ve Enable ile etkinleştir.
4. Google Auth Platform → Branding bölümünde görünen uygulama adını Planvori yap; destek/geliştirici e-postalarını kendi adresinle doldur. Konsol ek zorunlu bilgi isterse tamamla.
5. Audience bölümünde kişisel Gmail için External ve Testing kullan. Test users listesine bağlayacağın kendi Google e-posta adresini ekle. Bu bir Play Store yayını değildir.
6. Data Access → Add or remove scopes bölümünde yalnızca şunları ekle:
   - https://www.googleapis.com/auth/drive.appdata
   - https://www.googleapis.com/auth/userinfo.email
7. Clients → Create client → Android seç. Adını “Planvori Kisisel Android” yap.
8. Paket adı: `com.mehmetarif.sadegorev`
9. SHA-1 sertifika parmak izi: `3C:4F:B6:D0:EA:0A:92:04:B7:AB:D2:41:6C:3B:80:21:D2:63:50:14`
10. Create ile kaydet. Play Console uygulama sahipliği doğrulaması kişisel APK için gerekli değildir. Android istemcisinde client secret gerekmez; bana Google şifreni, erişim tokenını veya başka gizli anahtar gönderme.
11. Aynı imzalı Planvori 1.8 APK’yi uygulamayı silmeden kur.
12. Ayarlar → Veri yedekleme → Google Drive → “Google hesabını bağla ve yedekle”. Test kullanıcısı olarak eklediğin hesabı seç ve Google izin ekranını onayla.
13. İlk yedeğin tarihi uygulamada göründüğünde kayıt tamamlanmıştır. İstersen “Günlük otomatik yedek” anahtarını aç.

## Geri yükleme

Aynı Google hesabını bağla → Listeyi yenile → ilgili tarihte “Bu yedeği geri yükle” → kayıt sayısını kontrol edip onayla. Önce mevcut verilerin cihazda güvenlik ZIP’i oluşur. ZIP indirmek zorunda değilsin; Drive’dan doğrudan okunur.

Yedekler Drive’ın uygulamaya özel gizli alanındadır. Drive dosya listesinde görünmez; Planvori yedekleme ekranından yönetilir. Yerel ZIP paylaşma/geri yükleme seçenekleri de durur.

## Sorun olursa

- Kod 10 / OAuth hatası: Paket adı ve SHA-1’i aynen kontrol et. OAuth istemcisi Android olmalı ve Drive API aynı projede etkin olmalı. Değişikliğin yayılması için birkaç dakika beklemek gerekebilir.
- Erişim reddedildi: Bağladığın adresi test kullanıcılarına eklediğinden ve izinleri kabul ettiğinden emin ol.
- İzin yenilenmeli: Yedekleme ekranından yeniden bağlan/onayla. Test durumunun süre ve kullanıcı sınırları olabilir; süresiz otomatik erişim garantisi verilmez.
- Google Play Hizmetleri gereklidir. İnternet yokken görevler ve dersler yine cihazda çalışır; Drive yedeği bekler.

## Doğrulama sınırı

Bu çalışma oturumundan Google Cloud konsoluna erişilemedi; hesabında proje/API/OAuth istemcisi oluşturulmadı. Gerçek hesap bağlama, Drive yükleme/indirme ve Android arka plan çalışması bu oturumda test edilmedi. Bu adımlar uygulama tarafındaki kodu tamamlar, fakat Google hesabı bağlantısını yukarıdaki kurulum etkinleştirir.

Teknik dayanak:
- https://developer.android.com/identity/authorization
- https://developers.google.com/workspace/drive/api/guides/appdata
- https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification
