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
6. [Klasörler, mesajlar, arama kayıtları ve duvar kâğıtları](#klasörler-mesajlar-arama-kayıtları-ve-duvar-kâğıtları)
7. [Debloat](#debloat)
8. [Kablosuz hata ayıklama ve EasyADB](#kablosuz-hata-ayıklama-ve-easyadb)
9. [Yedekleri PC'ye taşıma](#yedekleri-pcye-taşıma)
10. [Doğrudan PC'ye yedekleme](#doğrudan-pcye-yedekleme)
11. [Ayarlar](#ayarlar)
12. [Bilinmesi gerekenler](#bilinmesi-gerekenler)

## Başlamadan önce

* Android 10 veya üstü.
* Magisk, KernelSU veya bunların fork'larından biriyle root. Root yöneticiniz sorduğunda EasyAndroid'e izin verin. Root yoksa Ana Sayfa açılır ama tüm işlemler soluk kalır.
* Ayarlardan başka bir klasör seçmezseniz yedekler dahili depolamadaki `EasyAndroid/Backups` klasörüne gider.

## Ana Sayfa

* **Genel bakış:** boş dahili depolama (ok, yedek klasörü ayarlarını açar), root durumu ve tek satırda Kablosuz Hata Ayıklama. Ayrıntılar için o satıra dokunun.
* **Hızlı işlemler:** her tür için (Uygulamalar, Mesajlar, Arama Kayıtları, Klasörler, Duvar Kâğıtları, Sistem) bir satır: kaç yedek olduğunu ve sonuncusunun ne kadar önce alındığını gösterir, 30 günden eskiyse turuncu olur. Satıra dokununca o türle yapabileceğiniz her şeyin olduğu bir sayfa açılır: yedekleme, en son yedeği geri yükleme, **Yedekleri göster** (listeyi **Geri Yükle**'de açar) ve **EasyADB ile bağlan**. Bir şey yapmayacak seçenekler soluktur.

## Uygulamaları yedekle

* **Uygulamaları yedekle** uygulama listesini hiçbiri seçili olmadan açar. İstediğiniz uygulamaları seçin ya da tümünü seçmek için seçme simgesine dokunun; bu, telefonun vazgeçemeyeceği uygulamalar (Sistem UI, Ayarlar, Shell, EasyAndroid'in kendisi ve UAD-NG listesinde güvensiz işaretli paketler) hariç hepsini seçer. Diyalogda uygulama verisi, OBB dosyaları, `Android/data` ve sıkıştırmayı seçersiniz. Diyalog kaç sistem uygulamasının dahil olduğunu söyler. Veri ancak uygulama kapalıyken tutarlıdır, bu yüzden seçili uygulamalar yedeklenirken kapatılır.
* **Uygulamalar** sayfasındaki **Bekleyen uygulamaları yedekle**, **Güncellenen uygulamaları yedekle** ve **Mevcut yedekleri yenile** uygulamaları sizin yerinize seçer: yedeği olmayanlar, kurulu sürümü yedeğinden yeni olanlar ve zaten yedeği olanlar.
* Her dosya SHA-256 özetiyle saklanır ve manifest en son yazılır, bu yüzden yarım kalan bir yedek "eksik yedek" olarak işaretlenir.

## Uygulamaları geri yükle

**Geri Yükle** sekmesini açın (veya Ana Sayfa'da **Uygulamalar**'a dokunup sayfasındaki geri yükleme seçeneklerini kullanın).

* **Uygulamalar** sayfasındaki **Tüm uygulamaları geri yükle**, **Eksik uygulamaları geri yükle** ve **Yeni sürümleri geri yükle** Geri Yükle'yi ilgili yedekler işaretli açar. Devam etmek için **Geri yükle**'ye basın.
* Neyin geri yükleneceğini seçersiniz: uygulama, verisi, OBB dosyaları ve izinler. Uygulama zaten kuruluysa üzerine yazabilir veya atlayabilirsiniz; bir uygulama için ya da hepsi için.
* Telefonda bir şey değişmeden önce tüm özetler doğrulanır.
* Önce uygulamalarınızı, sonra sistem yedeğini geri yükleyin; çünkü varsayılan uygulamalar için paketlerin kurulu olması gerekir.

## Sistem yedeği

Ana Sayfa'da **Sistem**'e dokunun ve **Sistem ayarlarını yedekle**'yi seçin.

* Ayarları, varsayılan uygulamaları ve Wi-Fi ağlarını tek tek ya da grup halinde seçin. Wi-Fi şifreleri yedek klasöründe şifrelenmeden saklandığı için Wi-Fi ağları varsayılan olarak seçili gelmez.
* Kayıtlı sistem yedekleri **Geri Yükle** sekmesinde listelenir. **Geri yükle** yalnızca telefondan farklı olanları gösterir. Her ROM'da aynı anlama gelen ayarlar işaretli gelir, diğerleri eski ROM'a özel olabilir ve seçimi size kalır.
* Kimlik, güvenlik ve durum ayarları (cihaz kimlikleri, kilit ekranı, SIM ayarları gibi) hiç saklanmaz ve geri yüklenmez.

## Klasörler, mesajlar, arama kayıtları ve duvar kâğıtları

Bunlar dosya kopyası olarak, her seferinde ayrı bir yedek halinde saklanır ve **Geri Yükle** sekmesinde kendi çiplerinin altında listelenir. Ana Sayfa'daki sayfaları aynı işi daha az dokunuşla yapar.

* **Klasörler:** Ana Sayfa'daki sayfasında veya **Geri Yükle** altındaki **Klasörler**'de **Klasör seç ve yedekle**'ye dokunun. Android klasörü ve yedek klasörü hiçbir zaman dahil edilmez.
* **Mesajlar**, **Arama Kayıtları** ve **Duvar Kâğıtları:** Ana Sayfa'daki sayfalarında veya **Geri Yükle** altındaki çiplerinde **Şimdi yedekle**'ye dokunun. Önce sorar. Veritabanı veya duvar kâğıdı dosyaları olduğu gibi kopyalanır, bu yüzden tek tek mesaj veya arama seçilemez.
* **Geri yükle**, mevcut dosyaları yedektekilerle değiştirir. Mesajları ve arama kayıtlarını yalnızca aynı Android sürümünde ve ROM'da geri yükleyin; geri yüklenen duvar kâğıdının görünmesi için telefonu yeniden başlatmanız gerekebilir.

## Debloat

* Liste UAD-NG projesinden gelir ve her paketin güvenlik seviyesi renkle gösterilir: **Önerilen** (yeşil), **İleri** (sarı), **Uzman** (turuncu), **Güvensiz** (kırmızı) ve **Listede yok** (gri).
* Paketleri seçip **Devre dışı bırak** veya **Kaldır**'a basın. Paketin tüm verileri, kalıntı kalmasın diye, devre dışı bırakılmadan veya kaldırılmadan önce silinir. Bu geri alınamaz. Paketin kendisi **Kaldırılanlar** ve **Geri getir** ile geri gelir, ama verileri gelmez. Güvensiz işaretli paketler kilitlidir.
* Arama kutusunun yanındaki üç nokta menüsü **Debloat profili**'ni dışarı ve içeri aktarır. Profil, kaldırdığınız uygulamaları içeren bir dosyadır; aynı temizlik yeni bir ROM'da ya da EasyADB'de uygulanabilir.

## Kablosuz hata ayıklama ve EasyADB

* Genel bakış kartındaki anahtarla **Kablosuz Hata Ayıklama**'yı açın (Android 11 veya üstü). Satırı bağlantı adresini ve bir PC'nin bağlı olup olmadığını gösterir, bağlıysa yeşil olur. Adres, eşleştirme düğmeleri ve fazlası için satıra dokunun. Bir PC bağlıyken kapatmak önce sorar.
* **QR ile eşleştir** ve **Kodla eşleştir** Android'in ilgili ekranlarını açar, Geliştirici seçeneklerinde aramanız gerekmez.
* PC'deki EasyADB'de **Ayarlar > Hata ayıklama** sekmesini açın, eşleştirin ve telefonda görünen adrese bağlanın.
* Ana Sayfa'daki her sayfanın sonundaki **EasyADB ile bağlan** aynı ayrıntıları açar ve kapalıysa Kablosuz Hata Ayıklama'yı açar.

## Yedekleri PC'ye taşıma

* Telefonu biçimlendirmeden önce dahili depolamadaki `EasyAndroid/Backups` klasörünü PC'ye kopyalayın, yeni ROM kurulduktan sonra geri kopyalayın. EasyADB klasörü doğrudan açabilir.
* Tek bir yedek **Geri Yükle**'den tek dosyalık `.easybckp` olarak dışa aktarılabilir ve **.easybckp içe aktar** ile geri alınabilir.
* Yedekler iki yönde de EasyADB biçimini kullanır, zstd ile sıkıştırılmış olanlar da.

## Doğrudan PC'ye yedekleme

Bunun için telefona adb ile (USB veya Kablosuz hata ayıklama) bağlı bir PC'de EasyADB açık olmalı ve telefonun kabuğuna (shell) root verilmiş olmalı. EasyADB isteyince root yöneticinizde izin verin.

* Bir yedekleme panelinde veya diyalogunda **Bu telefon** yerine **PC (EasyADB)** seçin. Bir PC bağlanana kadar soluk kalır. İlk seferde EasyADB PC'de sorar; sorulmaması için **Bu telefona güven**'i seçin. Tek yanıt bir yedeklemenin bütün uygulamalarını kapsar, 2 dakika yanıtlanmayan istek düşer. Beklerken telefonda **PC'de onay bekleniyor** ya da PC'nin meşgul olduğu yazar. Ret sonrasında kalanlar denenmez. Bir paneli her açtığınızda seçim **Bu telefon** olarak gelir.
* Uygulamalar okundukça PC'ye gönderilir ve telefonda yer kaplamaz. PC için zstd sıkıştırması kullanılamaz.
* Klasörler **Doğrudan PC'ye** gider ya da önce telefonda alınır; bunun için klasörler kadar boş yer gerekir. Mesajlar, arama kayıtları, duvar kâğıtları ve sistem ayarları her zaman önce telefonda alınır, sonra kopyalanır.
* PC kendi kopyasını doğruladığında uygulama telefondaki kopyanın silinip silinmeyeceğini sorar. Cevabınız olmadan hiçbir şey silinmez.
* PC'nin elindeki uygulamalar **Bekleyen uygulamaları yedekle** ve **Güncellenen uygulamaları yedekle** için yedeklenmiş sayılır.
* PC'den, EasyADB'nin **Telefon verileri** sekmesi bu telefondan mesaj, arama kaydı, duvar kâğıdı ve sistem ayarı yedeklerini ister ve kopyalar. Telefon önce bildirimle ve uygulama içinde sorar: **Reddet**, **Yalnızca bu sefer** veya **Bu bilgisayara güven**. 2 dakika yanıt verilmezse hiçbir şey başlamaz. Güvenilen bilgisayarlar Ayarlar'da **Güvenilen bilgisayarlar** altındadır. Bu soru yalnızca EasyAndroid'in yaptıklarını kapsar; adb üzerinden root'u olan bir PC telefonu kendisi de okuyabilir. Bildirimler kapalıysa EasyADB telefonda EasyAndroid'i açar.
* Kabuğa root vermek, telefonla eşleşmiş her PC'nin telefonda root komutu çalıştırabilmesi demektir. Yalnızca kendi PC'lerinizi eşleştirin.

## Ayarlar

* **Görünüm ve dil:** tema (Sistem, Açık, Koyu, Saf siyah) ve uygulama dili (İngilizce veya Türkçe, Android 13 veya üstü).
* **Depolama ve güvenlik:** yedek klasörü (dahili depolama veya SD kart), yedeklerin kapladığı alan ve önce soran, geri alınamayan **Tüm yedekleri sil**.
* **Gelişmiş araçlar:** **Wi-Fi aktarım hız testi**, telefon ile EasyADB'nin çalıştığı PC arasında her yönde 32 MB gönderir ve bir yedeğin ne kadar hızlı gidebileceğini gösterir; EasyADB önce PC'de her zaman sorar. **Güvenilen bilgisayarlar**, işleri sormadan başlayan PC'leri listeler. **Son işlem raporları** son 50 işin sonucunu listeler, **Uygulamayı yeniden başlat** EasyAndroid'i baştan yükler.
* **Bildirimler:** Android'in EasyAndroid bildirim ayarlarını açar.
* **Yardım ve bilgi:** bu sayfa, iletişim, depo ve hakkında diyaloğu.

## Bilinmesi gerekenler

* Verisini Android Keystore ile telefona bağlayan uygulamalar (bankacılık, doğrulayıcılar) geri yüklemeden sonra yeniden giriş isteyebilir.
* Wi-Fi ağlarını geri yüklemek için Android 11 veya üstü gerekir. Kurumsal ve WEP ağlar saklanmaz.
* Özel yedek klasörü dahili depolamada veya SD kartta olmalıdır. Bulut klasörlerinin root komutlarının kullanabileceği bir yolu yoktur.

<p align="right">(<a href="#readme-top">başa dön</a>)</p>
