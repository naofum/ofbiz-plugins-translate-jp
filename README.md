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
- 総務省の全国地方公共団体コードをダウンロードし、OFBiz の Geo（市区町村）として登録・更新できます。

> 翻訳適用の対象リソースは **UILabel のみ** です（デモデータなどの追加を予定）。
> 別機能として、総務省の全国地方公共団体コードを Geo データとして取り込めます。

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

### 地方公共団体コードのインポート

1. 「地方公共団体コード」欄の「地方公共団体コードのインポート」ボタンを押します。
2. 確認ダイアログで注意事項を確認し、実行します。
3. 結果（データ版・取込件数・Geo/GeoAssoc の更新内訳）が画面に表示されます。

処理内容:

- opendataapi.jp の GraphQL(BFF) を呼び出し、S3 署名付きダウンロードURLを取得して
  `data.jsonl`（1行1レコードの JSON Lines）をダウンロードします。
  - 生成元: `https://opendataapi.jp/data/providers/soumu/datasets/municipality_code/latest/`
- 全国地方公共団体コード（6桁）を次のように取り込みます。

  | 項目 | 値 |
  | --- | --- |
  | `geoId` | `JP-` + 5桁（JIS X0402 市区町村コード）例: `JP-13101` |
  | `geoCode` | 6桁（全国地方公共団体コード。チェックディジット込み）例: `131016` |
  | `geoTypeId` | `MUNICIPALITY` |
  | `abbreviation` | 市区町村名 |
  | `GeoAssoc` | 親 `JP-xx`（都道府県）→ 子 `JP-xxxxx`（市区町村）／`REGIONS` |

- 都道府県レコード（`GeoData_JP.xml` の `JP-01`〜`JP-47`）は変更しません。
- 既存データは更新（upsert）のみで、削除は行いません。再実行すると最新データに更新されます。
- データ取得はネットワーク経由のため、OFBiz の再起動は不要です。
- 政令指定都市の行政区（例: 札幌市中央区）はこのデータセットには含まれません。

> 出典：全国地方公共団体コード（総務省）／[opendataapi.jp](https://opendataapi.jp/) が作成（政府標準利用規約 第2.0版）。
> 帰属表示（クレジット）が必要です。

#### 社内プロキシ / SSL検査環境での注意

TLS検査を行うプロキシ配下では、Java が証明書を検証できず
`PKIX path building failed` で失敗することがあります。その場合は次のいずれかを行ってください。

- JDK のトラストストア（`cacerts`）にプロキシのルート証明機関を追加する。
- OFBiz 起動時に Windows のルートストアを使うよう指定する。

  ```bash
  ./gradlew ofbiz -PjvmArgs="-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT"
  ```

失敗時は画面のエラーメッセージにも同様の案内が表示されます。

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
├── src/main/java/          # Java サービス（地方公共団体コードのインポート）
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
