# Coldsnap

Плагин для Paper и Purpur 1.21.11. Переносит систему холода из оригинального [датапака Coldsnap](https://modrinth.com/datapack/coldsnap) от AuraAcorn. Температура в холодных биомах зависит от погоды, воды, укрытия, источников тепла и одежды. Плагин добавляет меховую броню, урон от холода и индикатор в action bar.

## Сборка

Нужен JDK 21. Gradle Wrapper скачает зависимости и включит Jumper в итоговый JAR.

```sh
./gradlew build
```

В Windows используйте `.\gradlew.bat build`. Готовый файл появится в `build/libs/Coldsnap-1.6.0.jar`.

## Установка и настройка

1. Поместите JAR в папку `plugins/` и запустите сервер.
2. Настройки появятся в `plugins/Coldsnap/config.jmc`. В репозитории есть [пример конфигурации](examples/config.jmc).
3. После правки примените настройки командой `/coldsnap reload` с разрешением `coldsnap.admin`.

Игроки могут управлять индикатором и смотреть статус командами `/coldsnap show`, `/coldsnap hide` и `/coldsnap status`.

WorldGuard и Nexo можно установить дополнительно. Флаг WorldGuard `coldsnap-cold deny` отключает холод в регионе. Тепло от брони Nexo задаётся по ID предмета в `warmth.nexoItems`.

## Лицензия

Порт распространяется по [MIT](LICENSE). Jumper включён на условиях [своей лицензии MIT](src/main/resources/META-INF/LICENSE-jumper.txt).
