<#--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

<div class="screenlet">
  <div class="screenlet-title-bar">
    <h3>${uiLabelMap.TranslateJpSelectTarget}</h3>
  </div>
  <div class="screenlet-body">

    <form method="post" action="<@ofbizUrl>applyTranslationJp</@ofbizUrl>">

      <table class="basic-table" cellspacing="0">
        <tbody>

          <#-- UIラベル（有効） -->
          <tr>
            <td class="label" style="width: 30px; text-align: center;">
              <input type="checkbox" name="targets" value="uilabel" id="chk_uilabel" checked="checked"/>
            </td>
            <td>
              <label for="chk_uilabel">
                <strong>${uiLabelMap.TranslateJpTargetUiLabel}</strong>
                &nbsp;
                <span class="tooltip">
                  (${uilabelFileCount!0} ${uiLabelMap.TranslateJpFileCount})
                </span>
              </label>
            </td>
          </tr>

          <#-- デモデータ（将来対応・グレーアウト） -->
          <tr style="color: #aaa;">
            <td class="label" style="width: 30px; text-align: center;">
              <input type="checkbox" name="targets_future" value="demodata" id="chk_demodata" disabled="disabled"/>
            </td>
            <td>
              <label for="chk_demodata" style="color: #aaa;">
                ${uiLabelMap.TranslateJpTargetDemoData}
              </label>
            </td>
          </tr>

        </tbody>
      </table>

      <#-- 注意書き -->
      <div style="margin: 16px 0 8px 0; padding: 10px; background: #fff8e1; border-left: 4px solid #ffc107;">
        <span style="color: #856404;">&#9888;&nbsp;${uiLabelMap.TranslateJpBackupNotice}</span><br/>
        <span style="color: #856404;">&#9888;&nbsp;${uiLabelMap.TranslateJpRestartNotice}</span>
      </div>

      <#-- 実行ボタン -->
      <div style="margin-top: 16px;">
        <input type="submit" value="${uiLabelMap.TranslateJpApplyButton}"
               class="buttontext"
               onclick="return confirm('${uiLabelMap.TranslateJpRestartNotice}');"/>
      </div>

    </form>

  </div>
</div>
