# ofbiz-plugins-translate-jp

Apache OFBiz の UI ラベル日本語翻訳を、本体ファイルへ安全に適用するためのプラグインです。

本リポジトリは **OFBiz の `plugins/translate-jp` ディレクトリ** に配置して動作させます。

---

## 概要

`translate-jp` は、Apache OFBiz の画面ラベル（UILabel）を日本語化するための管理用プラグインです。

- プラグイン内の `uilabel/` に配置された翻訳済み XML ファイルを、OFBiz 本体の同名ファイルへ上書きコピーします。
- 適用前に、元のファイルを `uilabel.bak/<タイムスタンプ>/` へ自動でバックアップします。
- 更新後は OFBiz の再起動が必要です。
- 管理者（`OFBTOOLS` 権限）のみアクセスできます。

> 現在の対象リソースは **UILabel のみ** です。今後、デモデータなどの追加を予定しています。

---

## 動作環境

- Apache OFBiz 24.09 系（本プラグインの開発・確認環境）
- Java / Gradle 環境は OFBiz 本体の要件に準じます

---

## インストール

1. 本リポジトリを OFBiz の `plugins/translate-jp` ディレクトリへ配置します。

   ```bash
   cd /path/to/ofbiz/plugins
   git clone <リポジトリURL> translate-jp
   ```

2. OFBiz を起動します。

   ```bash
   ./gradlew ofbiz
   ```

3. 管理ユーザーでログイン後、以下の URL へアクセスします。

   ```
   https://<host>:<port>/translate-jp/control/main
   ```

---

## 使い方

1. 「日本語翻訳の適用」画面で、更新対象 **UIラベル** にチェックを入れます。
2. 「更新を実行」ボタンを押します。
3. 確認ダイアログで注意事項を確認し、実行します。
4. 成功メッセージが表示されたら、OFBiz を再起動して変更を反映させます。

### バックアップについて

- 適用前に、更新対象となる OFBiz 本体ファイルが `plugins/translate-jp/uilabel.bak/yyyyMMdd_HHmmss/` へコピーされます。
- 元のファイルに戻したい場合は、バックアップから手動で復元してください。

---

## ディレクトリ構成

```
plugins/translate-jp/
├── config/                 # プラグイン独自の UILabel 定義
├── servicedef/             # サービス定義
├── src/main/groovy/        # Groovy サービス・スクリプト
├── webapp/translate-jp/    # Web 画面（ controller / FTL ）
├── widget/                 # スクリーン定義
├── uilabel/                # 翻訳済み UILabel XML ファイル群（適用対象）
├── uilabel.bak/            # 適用時に自動生成されるバックアップ（実行後に作成）
├── ofbiz-component.xml     # コンポーネント定義
├── build.gradle            # ビルド設定（外部依存なし）
└── README.md               # 本ファイル
```

---

## 注意事項

- 本プラグインは OFBiz 本体のファイルを上書きします。適用前に必ずバックアップを確認してください。
- 翻訳適用後は **必ず OFBiz を再起動** してください。UI ラベルの変更は再起動後に反映されます。
- 更新処理の途中でエラーが発生した場合、既にコピー済みのファイルは自動ではロールバックされません。必要に応じて `uilabel.bak/` から復元してください。
- 本プラグインへのアクセスには `OFBTOOLS` 権限が必要です。

---

## License

Licensed under the Apache License, Version 2.0.
See [LICENSE](LICENSE) for the full license text.

---

## 参考

- [Apache OFBiz](https://ofbiz.apache.org/)
