/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ofbiz.translatejp

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * UIラベルの日本語翻訳ファイルを OFBiz 本体に適用するサービス。
 *
 * 処理フロー:
 *   1. plugins/translate-jp/uilabel/ 下の翻訳済み XML ファイルを列挙する
 *   2. plugins/translate-jp/uilabel.bak/<yyyyMMdd_HHmmss>/ に OFBiz 本体側の
 *      現行ファイルをディレクトリー構造を維持してコピーする（バックアップ）
 *   3. uilabel/ 下の翻訳済みファイルを OFBiz 本体側の同一パスへ上書きコピーする
 *   4. 成功時: successMessage, backupPath, fileCount を OUT パラメータとして返す
 *   5. 例外発生時: エラーメッセージを返す（途中まで適用されたファイルはロールバックしない）
 */

def applyTranslationJp() {
    // --- 定数・パス解決 ---
    String ofbizHome   = System.getProperty("ofbiz.home")
    Path   homeDir     = Paths.get(ofbizHome).toAbsolutePath().normalize()
    Path   pluginDir   = homeDir.resolve("plugins/translate-jp")
    Path   uilabelDir  = pluginDir.resolve("uilabel")

    // 入力チェック
    String targets = parameters.targets ?: ""
    if (!targets.contains("uilabel")) {
        return error("更新対象が選択されていません。")
    }

    if (!Files.isDirectory(uilabelDir)) {
        return error("uilabel ディレクトリーが見つかりません: ${uilabelDir}")
    }

    // --- バックアップフォルダー作成 ---
    String timestamp  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    Path   bakBaseDir = pluginDir.resolve("uilabel.bak").resolve(timestamp)

    try {
        Files.createDirectories(bakBaseDir)
    } catch (Exception e) {
        return error("バックアップフォルダーの作成に失敗しました: ${e.message}")
    }

    // --- uilabel/ 下の全 XML ファイルを列挙 ---
    List<Path> srcFiles = []
    Files.walk(uilabelDir).each { Path p ->
        if (Files.isRegularFile(p) && p.toString().endsWith(".xml")) {
            srcFiles << p
        }
    }

    if (srcFiles.isEmpty()) {
        return error("uilabel フォルダーに XML ファイルが見つかりません。")
    }

    int backedUp = 0
    int copied   = 0
    String currentFile = ""

    try {
        srcFiles.each { Path srcFile ->
            // uilabelDir からの相対パスを計算
            // 例: uilabel/applications/accounting/config/AccountingUiLabels.xml
            //  → 相対パス: applications/accounting/config/AccountingUiLabels.xml
            Path relPath = uilabelDir.relativize(srcFile)
            currentFile  = relPath.toString()

            // OFBiz 本体側の対象ファイルパス
            Path destFile = homeDir.resolve(relPath)

            // バックアップ先パス
            Path bakFile = bakBaseDir.resolve(relPath)

            // --- (a) バックアップ: 本体側ファイルが存在すればコピー ---
            if (Files.exists(destFile)) {
                Files.createDirectories(bakFile.parent)
                Files.copy(destFile, bakFile, StandardCopyOption.REPLACE_EXISTING)
                backedUp++
            }

            // --- (b) 翻訳ファイルを本体側へコピー（上書き）---
            Files.createDirectories(destFile.parent)
            Files.copy(srcFile, destFile, StandardCopyOption.REPLACE_EXISTING)
            copied++
        }
    } catch (Exception e) {
        return error("ファイル \"${currentFile}\" の処理中にエラーが発生しました: ${e.message}")
    }

    // バックアップが空なら bak フォルダーを削除しておく
    if (backedUp == 0) {
        try { bakBaseDir.toFile().deleteDir() } catch (ignored) {}
    }

    String backupMsg = backedUp > 0
        ? "uilabel.bak/${timestamp}/"
        : "(バックアップ対象なし)"

    return success([
        successMessage: "日本語翻訳を適用しました（${copied} ファイル）。OFBizを再起動してください。",
        backupPath    : backupMsg,
        fileCount     : copied
    ])
}
