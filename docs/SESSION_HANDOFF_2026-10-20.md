# LumaLink 浼氳瘽瀛樻。锛?026-10-20 鍑屾櫒鏆傚仠锛?
## 缁撹涓€鍙ヨ瘽

**鐩告満鏂囦欢鍒楄〃宸茬粡鐪熸鎵撻€?*锛欸100D 閫氳繃 `accctrl` + UPnP `Browse` 杩斿洖 200 鍜岀湡瀹?DIDL锛孉pp 宸茶兘鏄剧ず `DO01002205.JPG` 绛?50 鏉★紙鎬绘暟 66锛夈€?**灏氭湭鎵撻€氱殑鏄?50001 鏂囦欢涓嬭浇**锛氱浉鏈哄 `DO...JPG` 杩斿洖 `unexpected end of stream` / `Connection reset`锛岄儴鍒嗗皾璇曚笅鍒颁竴鍗婂悗 JPEG 灏句笉瀹屾暣銆?鏄庡ぉ浠?**v1.0.7 鐨勪笅杞藉ご/鍒嗛〉淇** 缁х画銆?
## 鐗堟湰涓庝骇鐗?
- 鏈€鏂颁唬鐮侊細`v1.0.7` / versionCode 37
- APK锛歚D:\LumaLink\dist\LumaLink-1.0.7-debug.apk`
  - SHA-256 `5E619424417381C8371F21C7F91B8D81CB933545CFC55FC7BA202B099BBE36BA`
- 婧愮爜锛歚D:\LumaLink\dist\LumaLink-1.0.7-src.zip`
  - SHA-256 `2FD979722FECC0089AF24F537F3BE64C815C7A43165CC2142DC850F48EAAF2A9`
- 鏋勫缓锛歚BUILD SUCCESSFUL`锛?1 涓?JVM 娴嬭瘯閫氳繃锛宭int 0銆?- v1.0.7 鏂板锛?  - 涓嬭浇璇锋眰澶?`Accept-Encoding: identity`銆乣Connection: close`
  - Browse 鎸?`NumberReturned` / `TotalMatches` 鍒嗛〉锛堢洰鏍?66 鏉★紝涓嶅啀鍙湁 50锛?  - 鍘熷 SOAP 姝ｅ垯鎻愬彇 `<Result>`锛坴1.0.6 宸插姞锛?  - 鐩稿唽椤?杩炴帴椤甸兘浼氳嚜鍔ㄨ姹傝闂?
## 宸茬粡纭鐨勭浉鏈鸿涓?
鐩告満锛歀UMIX G100D锛孲SID `G100D-XXXXXX`锛屽簭鍒楀彿 `XXXXXXXXXXXX`锛屽浐浠?`VD3.80`銆?鎵嬫満锛歚192.168.54.10`锛岀浉鏈猴細`192.168.54.1`銆?
璁块棶娴佺▼锛?
```text
accctrl req_acc
-> ok_under_research_no_msg,...,remote,encrypted   锛堢瓑寰?鑷姩鎵瑰噯锛?-> 绗簩娆¤姹傝繑鍥?ok,...                             锛坰ession=false锛?-> playmode -> ok
-> UPnP Browse /Server0/CDS_control -> 200
```

鍏抽敭鍦板潃锛?
```text
POST http://192.168.54.1:60606/Server0/CDS_control
```

`/Lumix/Server0/CDS_control` 杩斿洖 404锛屽繀椤诲洖閫€鍒?`/Server0`銆?
鐪熷疄 DIDL锛堣妭閫夛級锛?
```xml
<item id="01002205">
  <dc:title>100-2205</dc:title>
  <res ... size="9940992">http://192.168.54.1:50001/DO01002205.JPG</res>
  <res ... size="5000">http://192.168.54.1:50001/DT01002205.JPG</res>
</item>
```

App 鍒楄〃鏄剧ず `DO01002205.JPG`銆乣DO01002207.JPG` 绛夛紝灏哄 9 MB 宸﹀彸锛屾潵婧愮湡瀹炪€?
## 褰撳墠闃诲锛?0001 涓嬭浇

鏃ュ織璇佹嵁锛?
```text
寮€濮嬩紶杈?DO01002208.JPG锛?0391040 瀛楄妭锛?浼犺緭澶辫触锛氭牎楠屽け璐ワ細JPEG 鏂囦欢灏句笉瀹屾暣
```

鍚庣画澶氭锛?
```text
unexpected end of stream on http://192.168.54.1:50001/...
Connection reset
```

鍗筹細璇锋眰宸插埌 50001锛屼絾鐩告満鎻愬墠鍏抽棴杩炴帴鎴栧彧鍙戜竴閮ㄥ垎銆?
鍙兘鍘熷洜锛坴1.0.7 宸插厛淇 1 椤癸級锛?
1. OkHttp 榛樿 `Accept-Encoding: gzip` 瀵艰嚧鐩告満 50001 涓嶅吋瀹?   - v1.0.7 宸叉敼涓?`identity`锛屽苟鍔?`Connection: close`
2. `session=false`锛氱浉鏈鸿繑鍥?`ok` 浣嗘病鏈?session token
   - 闇€瑕佺‘璁?`accctrl ok` 鍝嶅簲鍒板簳鏈夊嚑涓瓧娈?   - 鍙兘闇€瑕佹崲 clientId/clientName锛屾垨璧?`req_acc_g/req_acc_e`
3. 50001 鍙帴鍙?HTTP/1.0 鎴?`HttpURLConnection` 椋庢牸璇锋眰
   - 澶囬€夛細涓嬭浇灞傚崟鐙敤 `HttpURLConnection`锛屼笉鐢?OkHttp
4. 闇€瑕?`Range` 鍒嗗潡鎴?`Connection: keep-alive` 鐨勭壒瀹氱粍鍚?5. 鐩告満涓€娆″彧鍏佽涓€涓繛鎺ワ紝涓嬭浇鏃?UPnP 杩炴帴鏈畬鍏ㄩ噴鏀?   - 鐩墠 `Connection: close` 鍙兘甯姪

## 鏄庡ぉ浼樺厛鍋氱殑 5 浠朵簨

1. 瀹夎 v1.0.7锛屾祴涓€寮?JPEG 涓嬭浇銆?   - 鐪嬫棩蹇楁槸鍚︿粛 `unexpected end of stream` / `Connection reset`銆?2. 鎵嬫満娴忚鍣ㄧ洿鎺ユ墦寮€锛?   ```text
   http://192.168.54.1:50001/DO01002205.JPG
   ```
   - 鑳藉畬鏁翠笅杞斤細璇存槑鏄?App 璇锋眰澶?瀹㈡埛绔棶棰橈紱
   - 涔熶腑鏂細璇存槑鐩告満 50001 绛栫暐/浼氳瘽闂銆?3. 鍦?`LumixControlClient` 鐨?access 鏃ュ織閲屽鍔犲瓧娈垫暟閲忥紙涓嶈褰?token锛夛細
   ```text
   fields=4 / fields=5
   ```
   纭 `ok` 鏄惁鐪熺殑娌℃湁 session銆?4. 鑻?identity 鏃犳晥锛氭妸涓嬭浇灞傛敼鎴?`HttpURLConnection` + `X-SESSION_ID` + `Accept-Encoding: identity`锛屾寜 Lumatte `CameraHttp.open` 鐨勬柟寮忋€?5. 棰勮锛欴IDL 姣忔潯 item 绗簩涓?`<res>` 鏄?`DT...JPG` 缂╃暐鍥撅紱鍒楄〃鐩墠鍙彇绗竴涓?`DO...JPG`銆?   - 涓嬩竴姝ヨВ鏋?thumbnail URL锛岀敤缂╃暐鍥惧仛棰勮锛岄伩鍏嶆瘡寮犻瑙堜笅杞?9 MB 鍘熷浘銆?
## 閲嶈鏂囦欢

- 鍗忚/璁块棶/UPnP锛?  - `protocol/.../LumixControlClient.kt`锛坅ccess 寰幆銆乻ession锛?  - `protocol/.../UpnpContentDirectoryClient.kt`锛坄/Lumix` 404 鍥為€€銆丷esult 姝ｅ垯銆丏IDL 姝ｅ垯銆佸垎椤碉級
  - `protocol/.../LumixHttpClient.kt`锛堜笅杞藉ご identity/close锛?  - `protocol/.../CameraSessionStore.kt`
- 浼犺緭锛?  - `transport/.../transfer/TransferDownloader.kt`
  - `transport/.../session/ConnectionManager.kt`
- UI锛?  - `gallery/.../GalleryViewModel.kt` / `GalleryScreen.kt`锛堢浉鍐岃嚜鍔?access锛?  - `transport/.../ui/ConnectViewModel.kt`锛堣繛鎺ラ〉鑷姩 access锛?- 鍙傝€冨疄鐜帮紙鍙鐮旂┒锛夛細`D:\DeepSeek Harness\projects\LumaLink\.tmp-research\lumatte\`
  - 鏉ヨ嚜 `https://github.com/GameChicks/Lumatte`锛圞otlin锛孏PL-3.0锛孯EADME 鏀寔 G100D锛?  - 鍏抽敭锛歚LumixCameraSession.kt` access 寰幆銆乣CameraHttp.kt` `Accept-Encoding: identity` + `X-SESSION_ID`銆乣CameraDownload.kt`

## 鏋勫缓鐜

```powershell
$env:JAVA_HOME='D:\LumaLink\.dev\jdk'
$env:ANDROID_HOME='D:\LumaLink\.dev\android-sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$env:GRADLE_USER_HOME='D:\DeepSeek Harness\projects\LumaLink\.gradle-home'
$env:ANDROID_USER_HOME='D:\DeepSeek Harness\projects\LumaLink\.android'
Remove-Item Env:ANDROID_SDK_HOME -ErrorAction SilentlyContinue
Set-Location 'D:\DeepSeek Harness\projects\LumaLink'
.\gradlew.bat :protocol:test :app:assembleDebug :app:lintDebug --no-watch-fs --console=plain
```

鎵撳寘锛?
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package.ps1 -Version '1.0.7'
```

鍚屾鍒?`D:\LumaLink` 闇€瑕?`danger-full-access`锛坮obocopy 鎺掗櫎 build/.gradle-home/.android/dist/.git锛屽啀澶嶅埗 dist锛夈€?
## 瀹夊叏杈圭晫锛堜笉鍙橈級

- 涓嶅啓鍥轰欢/瀵勫瓨鍣?鍒嗗尯/绛惧悕
- 涓嶈嚜鍔ㄥ啓鐩告満銆佷笉鍒犻櫎鐩告満鏂囦欢
- 鎺у埗鍛戒护浠?`playmode/recmode/capture/video_recstart/video_recstop/startstream/stopstream`
- access 璇锋眰鐢辩敤鎴锋墦寮€鏂囦欢椤?鎸夐挳瑙﹀彂锛涗笉鑷姩閲嶈瘯鎺у埗鍛戒护
- 涓嶄吉閫犳祴璇曠粨鏋?
## 褰撳墠鐩爣鐘舵€?
涔嬪墠 goal 鏇捐鏍囪 blocked锛堝巶鍟嗗姞瀵嗙粨璁猴級锛涘悗鏉?GameChicks/Lumatte 璇佹槑 G100D 鍙€氳繃 access+Browse 璇诲彇銆?鐜板湪瀹為檯鐘舵€侊細**鏂囦欢鍒楄〃宸查€氾紝涓嬭浇鏈€?*銆傛槑澶╃户缁椂浼樺厛楠岃瘉 v1.0.7 涓嬭浇锛涘鏋滀笅杞介€氫簡锛屽啀琛ラ瑙堢缉鐣ュ浘鍜?66 鏉″垎椤甸獙璇併€?