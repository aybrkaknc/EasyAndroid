<a id="readme-top"></a>

<p align="center">
  <a href="README.md">English</a>
</p>

<br />
<div align="center">
  <h3 align="center">EasyAndroid</h3>

  <p align="center">
    Android telefonları doğrudan telefonda yedekleyen, geri yükleyen ve gereksiz uygulamalardan arındıran root uygulaması. <a href="https://github.com/aybrkaknc/EasyADB">EasyADB</a>'nin telefondaki yardımcısı.
  </p>
</div>



<details>
  <summary>İçindekiler</summary>
  <ol>
    <li>
      <a href="#proje-hakkında">Proje Hakkında</a>
      <ul>
        <li><a href="#özellikler">Özellikler</a></li>
        <li><a href="#kullanılan-teknolojiler">Kullanılan Teknolojiler</a></li>
      </ul>
    </li>
    <li>
      <a href="#başlarken">Başlarken</a>
      <ul>
        <li><a href="#gereksinimler">Gereksinimler</a></li>
        <li><a href="#kurulum">Kurulum</a></li>
      </ul>
    </li>
    <li><a href="#kullanım">Kullanım</a></li>
    <li><a href="#geliştirme">Geliştirme</a></li>
    <li><a href="#yol-haritası">Yol Haritası</a></li>
    <li><a href="#lisans">Lisans</a></li>
    <li><a href="#iletişim">İletişim</a></li>
    <li><a href="#teşekkürler">Teşekkürler</a></li>
  </ol>
</details>



## Proje Hakkında

Sık sık custom ROM değiştirmek, her seferinde tüm uygulamaları yeniden indirmek ve her şeyi baştan ayarlamak demektir. EasyAndroid uygulamaları verileri, izinleri ve ayarlarıyla telefonda saklar, ROM kurulumundan sonra geri getirir.

### Özellikler

* **Yedekleme ve geri yükleme:** APK ve bölünmüş APK'lar, uygulama verisi, cihaz korumalı veri, OBB ve isteğe bağlı olarak `Android/data`. Birçok uygulama birlikte seçilebilir. Her dosyanın SHA-256 özeti vardır ve telefonda bir şey değişmeden önce doğrulanır.
* **İzinler:** Çalışma zamanı izinleri, özel erişim ve pil istisnaları uygulamayla birlikte geri gelir.
* **EasyADB ile uyumlu:** Sıkıştırılmamış yedekler iki yönde de EasyADB biçimini kullanır. Tek dosyalık `.easybckp` dışa ve içe aktarılabilir. zstd sıkıştırması isteğe bağlıdır ve EasyADB tarafından henüz okunamaz.
* **Debloat:** UAD-NG listesinden açıklamalar ve güvenlik seviyeleri, toplu devre dışı bırakma veya kaldırma, geri getirme ve EasyADB ile paylaşılabilen profiller. Güvensiz olarak işaretlenen paketler kilitlidir.
* **Sistem:** Sistem ayarlarını, varsayılan uygulamaları ve Wi-Fi ağlarını saklar, geri yüklemeden önce yalnızca farkları listeler.
* **Kablosuz hata ayıklama:** Açıp kapatın, bağlantı adresini görün ve PC'deki EasyADB ile eşleşmek için QR veya eşleştirme kodu ekranını tek dokunuşla açın.
* **Kolaylık:** Türkçe ve İngilizce, Material You, ilerleme, iptal ve işlem sonunda rapor.

### Kullanılan Teknolojiler

* [Kotlin](https://kotlinlang.org/) ve [Jetpack Compose](https://developer.android.com/compose)
* [libsu](https://github.com/topjohnwu/libsu)
* [zstd-jni](https://github.com/luben/zstd-jni)

<p align="right">(<a href="#readme-top">başa dön</a>)</p>



## Başlarken

### Gereksinimler

* Android 10 veya üstü
* Magisk, KernelSU veya bunların fork'larından biriyle root

### Kurulum

1. APK'yı derleyin
   ```sh
   ./gradlew assembleDebug
   ```
2. Telefona kurun
   ```sh
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
3. Uygulamayı açın ve root erişimine izin verin.

<p align="right">(<a href="#readme-top">başa dön</a>)</p>



## Kullanım

ROM kurmadan önce:

1. **Uygulama:** uygulamaları seçip yedekleyin.
2. **Sistem:** bir sistem yedeği oluşturun.
3. **Debloat:** kaldırılan uygulamaları profil olarak kaydedin.
4. Depolama biçimlendirilecekse dahili depolamadaki `EasyAndroid/Backups` klasörünü PC'ye kopyalayın.

ROM kurduktan sonra klasörü geri kopyalayın, EasyAndroid'i kurun ve aynı sekmelerden geri yükleyin.

Bilinmesi gerekenler:

* Verisini Android Keystore ile telefona bağlayan uygulamalar (bankacılık, doğrulayıcılar) yeniden giriş isteyebilir.
* Wi-Fi şifreleri yedek klasöründe şifrelenmeden saklanır. Varsayılan olarak kapalıdır. Geri yüklemek için Android 11 veya üstü gerekir. Kurumsal ve WEP ağları saklanmaz.
* Özel yedek klasörü dahili depolamada veya SD kartta olmalıdır. Bulut klasörlerinin root komutlarının kullanabileceği bir yolu yoktur.

<p align="right">(<a href="#readme-top">başa dön</a>)</p>



## Geliştirme

```text
app/src/main/java/com/aybrkaknc/easyandroid/
  core/   yedekleme, geri yükleme, debloat ve sistem mantığı; cihaza yalnızca Root üzerinden erişir
  job/    çalışan işlemler, bildirim
  ui/     Compose ekranları
```

Birim testleri:
```sh
./gradlew testDebugUnitTest
```

Motor testleri şu değişkenler verildiğinde emülatörde çalışır, verilmezse atlanır:
```sh
EA_SERIAL=emulator-5554 EA_PACKAGE=com.aybrkaknc.easyandroid ./gradlew testDebugUnitTest --rerun
```

<p align="right">(<a href="#readme-top">başa dön</a>)</p>



## Yol Haritası

- [ ] Zamanlanmış yedekler
- [ ] EasyADB'de zstd yedekleri

<p align="right">(<a href="#readme-top">başa dön</a>)</p>



## Katkıda Bulunma

Katkılarınızı memnuniyetle karşılıyoruz. Büyük değişikliklerden önce bir issue açın, sonra pull request gönderin.

<p align="right">(<a href="#readme-top">başa dön</a>)</p>



## Lisans

GNU General Public License v3.0 ile dağıtılır. Ayrıntılar için [LICENSE](LICENSE) dosyasına bakın.

EasyAndroid, [Universal Android Debloater Next Generation](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation) paket verilerini içerir, GPL-3.0.

<p align="right">(<a href="#readme-top">başa dön</a>)</p>



## İletişim

Ayberk - [@aybrkaknc](https://github.com/aybrkaknc)

<p align="right">(<a href="#readme-top">başa dön</a>)</p>



## Teşekkürler

* [Best-README-Template](https://github.com/aybrkaknc/Best-README-Template)
* [Universal Android Debloater Next Generation](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation)
* [EasyADB](https://github.com/aybrkaknc/EasyADB)

<p align="right">(<a href="#readme-top">başa dön</a>)</p>
