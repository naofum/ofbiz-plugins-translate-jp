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
package org.apache.ofbiz.translatejp;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.transaction.TransactionUtil;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

/**
 * 総務省「全国地方公共団体コード」を opendataapi.jp からダウンロードし、
 * OFBiz の Geo / GeoAssoc として登録（insert/update）するサービス。
 *
 * <p>ダウンロードURLは opendataapi.jp のページ上で JavaScript により
 * 取得されるため、ブラウザと同一の GraphQL(BFF) を呼び出して
 * S3 署名付きURLを得る。署名は1時間で失効するため毎回取得する。</p>
 *
 * <p>マッピング（plugins/finance の先例に準拠）:
 * <ul>
 *   <li>geoId        = "JP-" + 5桁（JIS X0402 市区町村コード）例: JP-13101</li>
 *   <li>geoCode      = 6桁（全国地方公共団体コード。チェックディジット込み）例: 131016</li>
 *   <li>geoTypeId    = MUNICIPALITY</li>
 *   <li>abbreviation = 市区町村名</li>
 *   <li>GeoAssoc    = 親 "JP-xx"(都道府県) → 子 "JP-xxxxx"(市区町村) / REGIONS</li>
 * </ul>
 * 都道府県レコード（GeoData_JP.xml の JP-01..JP-47）は変更しない。</p>
 */
public final class MunicipalityCodeImporter {

    private static final String MODULE = MunicipalityCodeImporter.class.getName();

    private static final String DEFAULT_GRAPHQL_ENDPOINT = "https://bff.opendataapi.jp/graphql";
    private static final String GRAPHQL_ENDPOINT_PROPERTY = "translatejp.opendataapi.graphql";

    private static final String PROVIDER_ID = "soumu";
    private static final String DATASET_ID = "municipality_code";
    private static final String SCHEMA_VERSION = "latest";
    private static final String DATA_VERSION = "latest";
    private static final String FILE_FORMAT = "jsonl";

    private static final String GRAPHQL_QUERY = "query getDataFileDownloadUrl($providerId: String!, $datasetId: String!, "
            + "$schemaVersionName: String!, $dataVersionName: String!, $fileFormat: String!) { "
            + "dataFileDownloadUrl(providerId: $providerId, datasetId: $datasetId, schemaVersionName: $schemaVersionName, "
            + "dataVersionName: $dataVersionName, fileFormat: $fileFormat) }";

    private static final String GEO_TYPE_MUNICIPALITY = "MUNICIPALITY";
    private static final String GEO_ASSOC_TYPE_REGIONS = "REGIONS";

    private static final int CONNECT_TIMEOUT_SECONDS = 15;
    private static final int REQUEST_TIMEOUT_SECONDS = 120;

    private static final Pattern DATA_VERSION_PATTERN = Pattern.compile("/data_versions/[^/]+/[^/]+/([^/]+)/");

    private MunicipalityCodeImporter() {
    }

    /**
     * サービス本体。
     *
     * @param dctx    dispatch context
     * @param context service context
     * @return サービスの実行結果
     */
    public static Map<String, Object> importFromOpenDataApi(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        ObjectMapper mapper = new ObjectMapper();

        String sourceUrl = (String) context.get("sourceUrl");
        if (UtilValidate.isNotEmpty(sourceUrl)) {
            sourceUrl = sourceUrl.trim();
        }

        String jsonl;
        String dataVersion = "";
        try {
            if (UtilValidate.isNotEmpty(sourceUrl)) {
                Debug.logInfo("Downloading municipality code data from explicit sourceUrl", MODULE);
                jsonl = httpGetText(sourceUrl);
            } else {
                String endpoint = System.getProperty(GRAPHQL_ENDPOINT_PROPERTY, DEFAULT_GRAPHQL_ENDPOINT);
                String signedUrl = requestSignedDownloadUrl(endpoint, mapper);
                dataVersion = extractDataVersion(signedUrl);
                Debug.logInfo("Downloading municipality code data (data version " + dataVersion + ")", MODULE);
                jsonl = httpGetText(signedUrl);
            }
        } catch (Exception e) {
            Debug.logError(e, "Failed to download municipality code data", MODULE);
            return ServiceUtil.returnError(describeDownloadError(e));
        }

        int totalRecords = 0;
        int prefectureRecords = 0;
        int municipalityRecords = 0;
        int invalidRecords = 0;

        int geoInserted = 0;
        int geoUpdated = 0;
        int geoUnchanged = 0;
        int geoAssocInserted = 0;
        int geoAssocExisting = 0;
        int geoAssocUpdated = 0;
        int geoAssocSkippedNoParent = 0;

        boolean beganTransaction = false;
        try {
            beganTransaction = TransactionUtil.begin();

            for (String line : jsonl.split("\\R")) {
                if (UtilValidate.isEmpty(line) || !line.trim().startsWith("{")) {
                    continue;
                }
                JsonNode row = mapper.readTree(line);
                String code = row.path("code").asText(null);
                if (!isMunicipalityCode(code)) {
                    invalidRecords++;
                    continue;
                }
                totalRecords++;

                String municipalityName = row.path("municipality_name").asText(null);
                if (UtilValidate.isEmpty(municipalityName)) {
                    prefectureRecords++;
                    continue;
                }
                municipalityRecords++;

                String prefectureCode = code.substring(0, 2);
                String municipalityGeoId = "JP-" + code.substring(0, 5);
                String prefectureGeoId = "JP-" + prefectureCode;

                // --- Geo (市区町村) の insert/update ---
                GenericValue geo = delegator.findOne("Geo", UtilMisc.toMap("geoId", municipalityGeoId), false);
                if (geo == null) {
                    GenericValue newGeo = delegator.makeValue("Geo", UtilMisc.toMap(
                            "geoId", municipalityGeoId,
                            "geoTypeId", GEO_TYPE_MUNICIPALITY,
                            "geoName", municipalityName,
                            "geoCode", code,
                            "abbreviation", municipalityName));
                    delegator.create(newGeo);
                    geoInserted++;
                } else {
                    boolean changed = false;
                    if (!municipalityName.equals(geo.getString("geoName"))) {
                        geo.setString("geoName", municipalityName);
                        changed = true;
                    }
                    if (!code.equals(geo.getString("geoCode"))) {
                        geo.setString("geoCode", code);
                        changed = true;
                    }
                    if (!GEO_TYPE_MUNICIPALITY.equals(geo.getString("geoTypeId"))) {
                        geo.setString("geoTypeId", GEO_TYPE_MUNICIPALITY);
                        changed = true;
                    }
                    if (!municipalityName.equals(geo.getString("abbreviation"))) {
                        geo.setString("abbreviation", municipalityName);
                        changed = true;
                    }
                    if (changed) {
                        delegator.store(geo);
                        geoUpdated++;
                    } else {
                        geoUnchanged++;
                    }
                }

                // --- GeoAssoc (都道府県 → 市区町村) の insert/update ---
                GenericValue parentGeo = delegator.findOne("Geo", UtilMisc.toMap("geoId", prefectureGeoId), false);
                if (parentGeo == null) {
                    // 都道府県 Geo は本サービスでは作成しない（seed の JP-01..JP-47 を前提）
                    geoAssocSkippedNoParent++;
                    continue;
                }
                Map<String, Object> assocPk = UtilMisc.toMap("geoId", prefectureGeoId, "geoIdTo", municipalityGeoId);
                GenericValue assoc = delegator.findOne("GeoAssoc", assocPk, false);
                if (assoc == null) {
                    GenericValue newAssoc = delegator.makeValue("GeoAssoc", UtilMisc.toMap(
                            "geoId", prefectureGeoId,
                            "geoIdTo", municipalityGeoId,
                            "geoAssocTypeId", GEO_ASSOC_TYPE_REGIONS));
                    delegator.create(newAssoc);
                    geoAssocInserted++;
                } else if (!GEO_ASSOC_TYPE_REGIONS.equals(assoc.getString("geoAssocTypeId"))) {
                    assoc.setString("geoAssocTypeId", GEO_ASSOC_TYPE_REGIONS);
                    delegator.store(assoc);
                    geoAssocUpdated++;
                } else {
                    geoAssocExisting++;
                }
            }

            TransactionUtil.commit(beganTransaction);
        } catch (Exception e) {
            Debug.logError(e, "Failed to import municipality code data", MODULE);
            try {
                if (beganTransaction) {
                    TransactionUtil.rollback(beganTransaction, "Municipality code import failed", e);
                }
            } catch (Exception rollbackError) {
                Debug.logError(rollbackError, "Rollback failed", MODULE);
            }
            return ServiceUtil.returnError("地方公共団体コードの登録に失敗しました: " + e.getMessage());
        }

        String versionText = UtilValidate.isNotEmpty(dataVersion) ? dataVersion : "(最新)";
        StringBuilder summary = new StringBuilder();
        summary.append("データ版: ").append(versionText);
        summary.append(" / 取込: ").append(totalRecords).append(" 行");
        summary.append("（市区町村 ").append(municipalityRecords).append(" 件");
        summary.append("、都道府県 ").append(prefectureRecords).append(" 件はスキップ");
        if (invalidRecords > 0) {
            summary.append("、不正 ").append(invalidRecords).append(" 件");
        }
        summary.append("）");
        summary.append(" / Geo: 新規 ").append(geoInserted).append(" 件・更新 ").append(geoUpdated);
        summary.append("・変更なし ").append(geoUnchanged).append(" 件");
        summary.append(" / GeoAssoc: 新規 ").append(geoAssocInserted).append(" 件・既存 ").append(geoAssocExisting);
        summary.append("・関連更新 ").append(geoAssocUpdated).append(" 件");
        if (geoAssocSkippedNoParent > 0) {
            summary.append("（親Geoなし ").append(geoAssocSkippedNoParent).append(" 件）");
        }

        Map<String, Object> result = ServiceUtil.returnSuccess(
                "地方公共団体コードをインポートしました（市区町村 " + municipalityRecords + " 件）。");
        result.put("dataVersion", versionText);
        result.put("totalRecords", totalRecords);
        result.put("municipalityCount", municipalityRecords);
        result.put("prefectureCount", prefectureRecords);
        result.put("geoInserted", geoInserted);
        result.put("geoUpdated", geoUpdated);
        result.put("geoUnchanged", geoUnchanged);
        result.put("geoAssocInserted", geoAssocInserted);
        result.put("geoAssocExisting", geoAssocExisting);
        result.put("geoImportSummary", summary.toString());
        return result;
    }

    /**
     * ダウンロード失敗時の利用者向けメッセージを組み立てる。
     * TLS証明書検証エラーの場合は、プロキシ/SSL検査環境向けの対処方法を案内する。
     */
    private static String describeDownloadError(Exception e) {
        boolean sslError = false;
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SSLException) {
                sslError = true;
                break;
            }
            cause = cause.getCause();
        }
        if (sslError) {
            return "地方公共団体コードのダウンロードに失敗しました: TLS証明書を検証できません。"
                    + "プロキシ/SSL検査環境では、JDKのトラストストアにルート証明機関を追加するか、"
                    + "OFBiz起動時に -Djavax.net.ssl.trustStoreType=WINDOWS-ROOT を指定してください。"
                    + "（詳細: " + e.getMessage() + "）";
        }
        return "地方公共団体コードのダウンロードに失敗しました: " + e.getMessage()
                + "（手動URLによるフォールバックも利用できます）";
    }

    private static String requestSignedDownloadUrl(String endpoint, ObjectMapper mapper)
            throws IOException, InterruptedException {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("providerId", PROVIDER_ID);
        variables.put("datasetId", DATASET_ID);
        variables.put("schemaVersionName", SCHEMA_VERSION);
        variables.put("dataVersionName", DATA_VERSION);
        variables.put("fileFormat", FILE_FORMAT);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", GRAPHQL_QUERY);
        payload.put("variables", variables);

        String responseBody = httpPostJson(endpoint, mapper.writeValueAsString(payload));
        JsonNode root = mapper.readTree(responseBody);
        if (root.hasNonNull("errors")) {
            throw new IOException("GraphQL error: " + root.get("errors").toString());
        }
        String signedUrl = root.path("data").path("dataFileDownloadUrl").asText(null);
        if (UtilValidate.isEmpty(signedUrl)) {
            throw new IOException("GraphQL response did not contain a download URL");
        }
        return signedUrl;
    }

    private static String httpPostJson(String endpoint, String body) throws IOException, InterruptedException {
        HttpClient client = newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GraphQL endpoint returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static String httpGetText(String url) throws IOException, InterruptedException {
        HttpClient client = newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("Accept", "application/json, text/plain, */*")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("download URL returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static HttpClient newHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private static String extractDataVersion(String url) {
        Matcher matcher = DATA_VERSION_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * 全国地方公共団体コード（6桁、チェックディジット込み）として妥当か検証する。
     * チェックディジットは (11 - (重み付き和 mod 11)) % 10 で計算する。
     */
    private static boolean isMunicipalityCode(String code) {
        if (code == null || code.length() != 6) {
            return false;
        }
        int[] weights = {6, 5, 4, 3, 2};
        int sum = 0;
        for (int i = 0; i < 5; i++) {
            char digit = code.charAt(i);
            if (digit < '0' || digit > '9') {
                return false;
            }
            sum += (digit - '0') * weights[i];
        }
        char check = code.charAt(5);
        if (check < '0' || check > '9') {
            return false;
        }
        int expected = (11 - (sum % 11)) % 10;
        return expected == (check - '0');
    }
}
