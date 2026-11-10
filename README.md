# Planvori — Android kaynak kodu

Siyah temalı, çevrimdışı çalışan kişisel görev ve zaman yönetimi uygulaması.

## Özellikler

- Başlık, açıklama, son tarih/saat, öncelik ve hatırlatma ile görev oluşturma
- Bugün, yaklaşan, geciken, tamamlanan ve tüm görev filtreleri
- Görevler ekranındaki filtre kartlarını basılı tutup sürükleyerek sıralama
- Aylık takvim, tek dokunuşla tamamlama ve yeniden aktifleştirme
- Room ile cihazda kalıcı depolama
- Uygulama kapalıyken AlarmManager bildirimleri ve yeniden başlatma sonrası yeniden planlama
- Filtre, öncelik, sıralama ve tema özelleştirilebilen Glance ana ekran widget'ı
- Ayarlar > Veri yedekleme ekranından ZIP dışa aktarımı, paylaşımı ve onaylı geri yükleme
- Tam sayfa görev oluşturma/düzenleme ve tarih/hatırlatma ekranları
- Karanlık, aydınlık ve lacivert tema; yumuşak geçişler ve yenilenen alt gezinme
- Basılı tutup sürüklemede hareket eden filtre kartı ve vurgulanan bırakma konumu
- Haftalık ders programı ekranı, tam sayfa ders ekleme ve günün derslerini gösteren ikinci ana ekran widget'ı
- Seçili güne doğrudan ders ekleme; ders widget'ında Tüm günler/Pazartesi–Pazar seçimi
- Her iki widget için ortak yuvarlak görünüm ve renk ayarı; Ayarlar içinde ayrı Widget yönetimi bölümü
- ZIP yedeğinde görevler, dersler ve ayarlar; önceki v1 ZIP yedekleri de içe aktarılır
- Android 13+ bildirim izni ve Android 12+ kesin alarm izni yönlendirmesi

## Android Studio ile çalıştırma

1. Android Studio'nun güncel kararlı sürümünü kurun.
2. Bu klasörü **Open** ile açın.
3. Android Studio isterse Android SDK 36 ve Gradle bağımlılıklarını yüklemesine izin verin.
4. Telefonunuzda geliştirici seçenekleri ve USB hata ayıklamayı açıp telefonu bağlayın.
5. Üst çubuktan telefonu seçip **Run** düğmesine basın.

## APK oluşturma

Android Studio'da **Build > Build Bundle(s) / APK(s) > Build APK(s)** yolunu izleyin. Debug APK `app/build/outputs/apk/debug/app-debug.apk` altında oluşur.

Projedeki `signing/tasklist.keystore`, önceki Tasklist APK'ları ile aynı imzayı kullanır. Bu anahtarı kaybetmeyin veya herkese açık depoya koymayın; farklı anahtar ile imzalanan APK doğrudan güncellenemez. Paket adı `com.mehmetarif.sadegorev` ve veritabanı adı `sade-gorev.db` korunur. Room 1→2 göçü yalnızca yeni ders tablosunu ekler; mevcut görevleri silmez. APK'yı mevcut kurulumun üzerine yükleyin, uygulamayı kaldırıp yeniden kurmayın.

Geri yükleme mevcut görevlerin yerini alır. Onaydan önce mevcut görevlerin güvenlik yedeği özel uygulama alanına kaydedilir; yedekleme ekranında bu dosya paylaşılabilir. ZIP dosyası şifreli değildir.

İlk açılışta bildirim iznini verin. Kesin saatli hatırlatma kapalıysa uygulama yaklaşık zamanlı alarm kurar ve Ayarlar ekranından sistem iznini açmanız için yönlendirir.

Dersler sekmesinde günü seçip **Ders ekle** düğmesine basın; seçtiğiniz gün forma aktarılır. Telefonun ana ekranında boş alana uzun basıp **Widget'lar > Planvori > Ders programı** öğesini ekleyin. Widget başlığına dokunarak Tüm günler veya haftanın bir gününü seçebilirsiniz. İki widget'ın görünümünü **Ayarlar > Widget yönetimi** içinden değiştirebilirsiniz.

## Doğrulama

`assembleRelease` ve `testDebugUnitTest` başarılıdır (33 test). APK bütünlüğü ve sertifikası doğrulandı. Sertifika SHA-256: `9258153199d77aba0c2f547211455ef8a2e02353729174c55a59d28d2ccadfee`. Bu ortamda Android cihaz/emülatör olmadığı için gerçek cihazda sürükleme, widget ve bildirim akışları denenemedi.

## 1.5 güncellemesi

Widget görevleri ve görünüm ayarları açık Glance oturumu boyunca Flow ile izlenir. Tamamlanan görevler listeden çıkar; Bugün/Tüm dersler seçimi yeniden oturum başlatmadan değişir. Liste satırlarının üzerinden geçen ayırıcı çizgiler kaldırılmıştır. Lacivert varsayılan vurgu, beş renk seçeneği, otomatik/açık/koyu görünüm, oval alt gezinme ve sade ayar bölümleri eklendi. Premium kısıtlaması bu sürümde yoktur.

## 1.6 güncellemesi

Uygulama adı Planvori olarak değiştirildi, lacivert zeminli yeni P logosu ve Android temalı simgesi eklendi. Paket, imza, veritabanı ve eski ZIP yedek formatı korunmuştur. Görev widget başlığı ve logosu, uygulamanın Görevler ekranını widget ile aynı filtrede açar. Tarihsiz görevler uygulama ve widget Bugün görünümüne dahildir. Yeni kurulumlarda widget tüm görevlerle başlar; mevcut filtre tercihiniz korunur. Yaklaşan/Geciken filtresi yeni tarihsiz görevleri kapsamaz; Tümü sekmesiyle görebilirsiniz. Görev sayısı sınırını aşan listede Tümünü aç bağlantısı bulunur. Uygulama güncellemesi ve telefon yeniden başlatılması sonrası iki widget yeniden çizilir. Alarm işlemlerinden bağımsız olarak veritabanı değişikliklerinden sonra widget yenilemesi yapılır. Yeni görev eklenirken açık widget veri akışının yenilendiği birim testiyle doğrulanmıştır.

Planvori adı için web ve Google Play alanına yönelik aramalarda aynı adlı bir uygulama bulunamadı; bu arama mağaza adı veya marka haklarının kesin kullanılabilirliğini garanti etmez.

## 1.7 güncellemesi

Her boyutta iki widget da tüm eşleşen öğeleri dikey kaydırılabilir Glance LazyColumn içinde gösterir. Tek öğe ve 3/5/8 öğe sınırları kaldırıldı; eski sınır ayarları okunabilir kalır fakat listeleri kesmez. Logo ve uygulama adı yerine Görevlerim ve Ders Programı başlıkları gösterilir. Başlık seçimi, ana ekran üzerinde yuvarlak bir seçim paneli açar; ek izin gerekmez. Ders Programı: Tüm günler üstte, ardından Pazartesi–Pazar. Görevlerim: Tüm öncelikler, Yüksek, Orta, Düşük. Öncelik seçimi tüm tarihleri kapsar ve yüksek öncelikten düşüğe sıralar. Panelde uygulama ekranını açmak için ayrı bir bağlantı vardır. Seçimler ZIP yedeğine dahildir; eski yedekler desteklenir. Görevler tamamlanınca listeden çıkar; ders satırı ilgili günü, + seçili güne ait ders formunu açar.

Kaydırma ve seçim paneli bu ortamda gerçek Android ana ekranında denenemedi. Birim testler uzun listelerin kesilmemesini, yedi günün filtrelenmesini, canlı öncelik değişimini ve yeni ayarların yedekten okunmasını doğrular.

## 1.8 — Google Drive

Ayarlar → Veri yedekleme → Google Drive. OAuth bağlantısı, ilk bağlantıda doğrudan yedekleme, manuel Drive yedeği ve geri yükleme, son 100 yedek ve isteğe bağlı günlük WorkManager yedeklemesi. Kurulum: GOOGLE_DRIVE_KURULUM.md. Paket/imza aynı, client secret ve refresh token APK’ye eklenmez; token yalnızca bellekte tutulur. Drive appDataFolder yedekleri normal dosya listesinde görünmez. İzinler appdata ve hesap eşleştirmesi için email ile sınırlıdır. HTTPS kullanılır. Her yükleme ayrı dosyadır; önceki yedekler otomatik silinmez. Özel önbellekteki geçici ZIP yükleme sonrası silinir. ZIP ayrıca şifrelenmez. Hesap bağlantısı ZIP/sistem yedeğine dahil edilmez. Bağlantı kaldırma otomatik işi durdurur ve Google iznini iptal eder.

Günlük yedekleme kesin saatli değildir; internet/pil/Android kısıtlamalarına tabidir. Zorla durdurma yeniden açılana kadar işleri durdurabilir. İzin gerektiğinde arka planda onay ekranı açılmaz; yedekleme ekranından bağlantı kontrolü istenir. Cloud konsoluna bu oturumdan erişilemedi; hesap/proje/OAuth yapılandırması ve gerçek Drive işlemleri doğrulanmadı.

## 1.9 — otomatik ve görünür hatırlatma

Yeni görevde tarih/saat belirlendiğinde varsayılan hatırlatma görev saatidir. Tarihsiz görevler alarm oluşturmaz. Hatırlatma sayfasında seçili seçenek radyo işareti, belirgin kenarlık ve renk ile gösterilir; ana düzenleme sayfasında da seçenek adı yazılır. Seçtikten sonra Tamam ile görev ekranına dönülür. Hatırlatma yok seçilebilir. Görev saatinde ve dakika önce seçenekleri tarih değişikliğini takip eder; özel hatırlatma seçilen mutlak tarih/saatte kalır. Mevcut tarihli görevlerde kapalı hatırlatmalar değiştirilmez.

Bildirim ve kesin alarm izinleri kapalıysa görev ekranında açıklama ve izin açma düğmesi gösterilir. Geçmiş hatırlatma zamanları için alarm kurulmaz; kullanıcıya açıklanır. Paket adı, imza ve Room şeması değişmedi. Bildirimlerin gerçek Android cihazındaki zamanlaması ve ekran görselleri bu ortamda doğrulanamadı; derleme ve 33 birim testi başarılıdır.

## 1.10 — ders hatırlatmaları

Ders ekleme/düzenleme ekranında **Hatırlatma** seçimi: Yok, Ders başında, 5/10/15/30 dk önce, 1 saat önce. Yeni derslerde varsayılan 10 dk öncedir; güncellemeden önce eklenmiş dersler kapalı başlar, istenirse tek tek açılır. Hatırlatma her hafta dersin gününde tekrarlanır: bildirim gelince bir sonraki haftanınki kurulur. Bildirimde ders adı, saat aralığı ve derslik yazar; dokununca Ders programı o günle açılır. Hatırlatması açık dersler listede zil simgesiyle gösterilir.

Dersler ayrı **Ders hatırlatmaları** bildirim kanalını kullanır; Android ayarlarından görev bildirimlerinden bağımsız kapatılabilir. Telefon yeniden başlatıldığında, uygulama güncellendiğinde, saat veya saat dilimi değiştiğinde alarmlar yeniden kurulur. Hatırlatma ayarı ZIP/Drive yedeğine eklenir; eski yedeklerdeki dersler hatırlatmasız gelir. Room şeması 2→3: `courses` tablosuna `reminderMinutes` sütunu eklenir, mevcut veriler silinmez.

`assembleRelease` ve `testDebugUnitTest` başarılıdır (42 test). APK önceki sürümlerle aynı sertifikayla imzalıdır (SHA-256 `9258153199d77aba0c2f547211455ef8a2e02353729174c55a59d28d2ccadfee`). Bildirimlerin gerçek cihazda zamanlaması ve 2→3 veritabanı göçü bu ortamda cihaz olmadığı için denenemedi.

## 2.0 — yeni arayüz

Uygulamanın tamamı ortak bir tasarım sistemiyle (`DesignSystem.kt`) yeniden çizildi: yeni açık/koyu/gece mavisi renk paletleri, daha güçlü tipografi, ince kenarlıklı kartlar, renkli ikon kutuları ve tutarlı boşluklar.

- **Görevler:** selamlama ve tarih başlığı, bugünkü ilerleme kartı (tamamlanma halkası, geciken/yaklaşan sayıları), sayaçlı filtre hapları, tarih sıralamasında Gecikmiş/Bugün/Yarın/Bu hafta/Daha sonra/Tarihsiz grupları. Görev kartında öncelik renginde onay kutusu, not önizlemesi, "Bugün · 17:00" gibi okunur tarih, hatırlatma ve yüksek öncelik işaretleri.
- **Kaydırma hareketleri:** görevi sağa kaydır → tamamla, sola kaydır → sil. İkisi de alttaki çubuktan **Geri al** ile geri alınabilir.
- **Takvim:** kart içinde ay görünümü, bugün halkası, öncelik renginde görev noktaları, "Bugün" kısayolu.
- **Ders programı:** tarihli hafta şeridi, saat sütunlu zaman çizelgesi, derslik/öğretmen/hatırlatma bilgisi, "Şimdi" ve "Sıradaki" rozetleri.
- **Görev/ders düzenleme:** büyük başlık alanı, Bugün/Yarın/Haftaya hızlı tarih seçimi, renkli öncelik seçici, düzenleme ekranından silme, saat kutuları ve renk seçimi.
- **Ayarlar ve yedekleme:** gruplanmış ayar kartları, tema önizlemeli görünüm seçimi, izin durumunu gösteren bildirim sayfası.

Ekran görüntüleri cihaz olmadan Robolectric ile üretilebilir: `./gradlew testDebugUnitTest -Pscreenshots=1 --tests '*ScreenshotTest*'` → `build/screenshots/`. Robolectric'in Android jar'ı önceden indirildiyse `-ProbolectricDir=<klasör>` ile çevrimdışı kullanılır. Normal test çalıştırmasında bu testler atlanır.

`assembleRelease` ve `testDebugUnitTest` başarılıdır (44 test); APK aynı sertifikayla imzalıdır. Kaydırma hareketleri ve animasyonlar gerçek cihazda denenmedi.
