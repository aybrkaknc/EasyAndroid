<a id="readme-top"></a>

<p align="center">
  <a href="HOW_TO_USE.md">English</a> | <a href="../README.tr.md">README'ye dön</a>
</p>

# EasyAndroid nasıl kullanılır

EasyAndroid, root'lu bir Android telefonda PC olmadan uygulamaları yedekler, geri yükler ve gereksiz uygulamalardan arındırır. Bu sayfa her ekranı sırayla anlatır.

## İçindekiler

1. [Başlamadan önce](#başlamadan-önce)
2. [Ana Sayfa](#ana-sayfa)
3. [Uygulamaları yedekle](#uygulamaları-yedekle)
4. [Uygulamaları geri yükle](#uygulamaları-geri-yükle)
5. [Sistem yedeği](#sistem-yedeği)
6. [Debloat](#debloat)
7. [Kablosuz hata ayıklama ve EasyADB](#kablosuz-hata-ayıklama-ve-easyadb)
8. [Yedekleri PC'ye taşıma](#yedekleri-pcye-taşıma)
9. [Ayarlar](#ayarlar)
10. [Bilinmesi gerekenler](#bilinmesi-gerekenler)

## Başlamadan önce

* Android 10 veya üstü.
* Magisk, KernelSU veya bunların fork'larından biriyle root. Root yöneticiniz sorduğunda EasyAndroid'e izin verin. Root yoksa Ana Sayfa açılır ama tüm işlemler soluk kalır.
* Ayarlardan başka bir klasör seçmezseniz yedekler dahili depolamadaki `EasyAndroid/Backups` klasörüne gider.

## Ana Sayfa

* **Genel bakış:** boş dahili depolama (ok, yedek klasörü ayarlarını açar), root durumu ve root yöneticinizin sürümü, Kablosuz hata ayıklama.
* **Kategori çipleri:** Uygulamalar ve Sistem kendi bölümlerine kaydırır, WiFi sistem yedeği seçimini açar. Mesajlar, Arama Kayıtları, Klasörler ve Duvar Kâğıtları henüz yok ve "Yakında" der.
* **Hızlı işlemler:** sabitleyebileceğiniz kartlar. Hepsini görmek ve kullandıklarınızı sabitlemek için **Daha fazla hızlı işlem**'i açın. Kartın üstündeki raptiye kartı Ana Sayfa'ya ekler veya kaldırır.

## Uygulamaları yedekle

* **Tüm uygulamaları yedekle**, telefonun vazgeçemeyeceği uygulamalar hariç hepsini yedekler (Sistem UI, Ayarlar, Shell, EasyAndroid'in kendisi ve UAD-NG listesinde güvensiz işaretli paketler). Diyalogda uygulama verisi, OBB dosyaları, `Android/data` ve sıkıştırmayı seçersiniz. Diyalog kaç sistem uygulamasının dahil olduğunu söyler. Veri ancak uygulama kapalıyken tutarlıdır, bu yüzden seçili uygulamalar yedeklenirken kapatılır.
* **Seçerek yedekle** uygulamaları tek tek seçtirir.
* **Bekleyen**, **güncellenen** ve **yenile** kartları uygulamaları sizin yerinize seçer: yedeği olmayanlar, kurulu sürümü yedeğinden yeni olanlar ve zaten yedeği olanlar.
* Her dosya SHA-256 özetiyle saklanır ve manifest en son yazılır, bu yüzden yarım kalan bir yedek "eksik yedek" olarak işaretlenir.

## Uygulamaları geri yükle

**Yedekler** sekmesini açın (veya Ana Sayfa'daki **Geri yükle** kartlarında **Cihazdan**'a dokunun).

* Ana Sayfa'daki **Tüm uygulamaları geri yükle**, **Eksik uygulamaları geri yükle** ve **Yeni sürümleri geri yükle** kartları Yedekler'i ilgili yedekler işaretli açar. Devam etmek için **Geri yükle**'ye basın.
* Neyin geri yükleneceğini seçersiniz: uygulama, verisi, OBB dosyaları ve izinler. Uygulama zaten kuruluysa üzerine yazabilir veya atlayabilirsiniz; bir uygulama için ya da hepsi için.
* Telefonda bir şey değişmeden önce tüm özetler doğrulanır.
* Önce uygulamalarınızı, sonra sistem yedeğini geri yükleyin; çünkü varsayılan uygulamalar için paketlerin kurulu olması gerekir.

## Sistem yedeği

Ana Sayfa'da **Sistemi yedekle**'yi açın.

* Ayarları, varsayılan uygulamaları ve Wi-Fi ağlarını tek tek ya da grup halinde seçin. Wi-Fi şifreleri yedek klasöründe şifrelenmeden saklandığı için Wi-Fi ağları varsayılan olarak seçili gelmez.
* Kayıtlı sistem yedekleri **Yedekler** sekmesinde listelenir. **Geri yükle** yalnızca telefondan farklı olanları gösterir. Her ROM'da aynı anlama gelen ayarlar işaretli gelir, diğerleri eski ROM'a özel olabilir ve seçimi size kalır.
* Kimlik, güvenlik ve durum ayarları (cihaz kimlikleri, kilit ekranı, SIM ayarları gibi) hiç saklanmaz ve geri yüklenmez.

## Debloat

* Liste UAD-NG projesinden gelir ve her paketin güvenlik seviyesi renkle gösterilir: **Önerilen** (yeşil), **İleri** (sarı), **Uzman** (turuncu), **Güvensiz** (kırmızı) ve **Listede yok** (gri).
* Paketleri seçip **Devre dışı bırak** veya **Kaldır**'a basın. İkisi de **Kaldırılanlar** ve **Geri getir** ile geri alınabilir. Güvensiz işaretli paketler kilitlidir.
* Arama kutusunun yanındaki üç nokta menüsü **Debloat profili**'ni dışarı ve içeri aktarır. Profil, kaldırdığınız uygulamaları içeren bir dosyadır; aynı temizlik yeni bir ROM'da ya da EasyADB'de uygulanabilir.

## Kablosuz hata ayıklama ve EasyADB

* Genel bakış kartından **Kablosuz hata ayıklama**'yı açın (Android 11 veya üstü). Bağlantı adresini ve bir PC'nin bağlı olup olmadığını gösterir.
* **QR ile eşleştir** ve **Kodla eşleştir** Android'in ilgili ekranlarını açar, Geliştirici seçeneklerinde aramanız gerekmez.
* PC'deki EasyADB'de Kablosuz sekmesini açın, eşleştirin ve telefonda görünen adrese bağlanın.
* Hızlı işlem kartlarındaki **EasyADB ile** düğmeleri aynı bilgiyi bir diyalogda gösterir ve kapalıysa kablosuz hata ayıklamayı açar.

## Yedekleri PC'ye taşıma

* Telefonu biçimlendirmeden önce dahili depolamadaki `EasyAndroid/Backups` klasörünü PC'ye kopyalayın, yeni ROM kurulduktan sonra geri kopyalayın. EasyADB klasörü doğrudan açabilir.
* Tek bir yedek **Yedekler**'den tek dosyalık `.easybckp` olarak dışa aktarılabilir ve **.easybckp içe aktar** ile geri alınabilir.
* Sıkıştırılmamış yedekler iki yönde de EasyADB biçimini kullanır. zstd ile sıkıştırılmış yedekler daha küçüktür ama EasyADB bunları henüz okuyamaz.

## Ayarlar

* **Görünüm ve dil:** tema (Sistem, Açık, Koyu, Saf siyah) ve uygulama dili (İngilizce veya Türkçe, Android 13 veya üstü).
* **Yedekleme içeriği:** yedek diyaloğunun varsayılanları (veri, OBB, harici veri, sıkıştırma) ve sistem yedeği seçimi.
* **Depolama ve güvenlik:** yedek klasörü (dahili depolama veya SD kart), yedeklerin kapladığı alan ve önce soran, geri alınamayan **Tüm yedekleri sil**.
* **Gelişmiş araçlar:** **Son işlem raporları** son 50 işin sonucunu listeler, **Uygulamayı yeniden başlat** EasyAndroid'i baştan yükler.
* **Bildirimler:** Android'in EasyAndroid bildirim ayarlarını açar.
* **Yardım ve bilgi:** bu sayfa, iletişim, depo ve hakkında diyaloğu.

## Bilinmesi gerekenler

* Verisini Android Keystore ile telefona bağlayan uygulamalar (bankacılık, doğrulayıcılar) geri yüklemeden sonra yeniden giriş isteyebilir.
* Wi-Fi ağlarını geri yüklemek için Android 11 veya üstü gerekir. Kurumsal ve WEP ağlar saklanmaz.
* Özel yedek klasörü dahili depolamada veya SD kartta olmalıdır. Bulut klasörlerinin root komutlarının kullanabileceği bir yolu yoktur.
* Mesajlar, arama kayıtları, klasörler ve duvar kâğıtları planlanıyor, henüz yok.

<p align="right">(<a href="#readme-top">başa dön</a>)</p>
