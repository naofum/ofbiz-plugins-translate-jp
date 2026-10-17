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

/**
 * uilabel/ フォルダー下の XML ファイル数を数えて context に設定するスクリプト。
 * TranslateJpScreens.xml の main スクリーンから呼ばれる。
 */

import java.nio.file.Files
import java.nio.file.Paths

String ofbizHome  = System.getProperty("ofbiz.home")
def    uilabelDir = Paths.get(ofbizHome, "plugins", "translate-jp", "uilabel")

int count = 0
if (Files.isDirectory(uilabelDir)) {
    Files.walk(uilabelDir).each { p ->
        if (Files.isRegularFile(p) && p.toString().endsWith(".xml")) {
            count++
        }
    }
}

context.uilabelFileCount = count
