# LumaLink v1.3.1 浜や粯璇存槑

鏃ユ湡锛?026-10-20
鐩爣璁惧锛歅anasonic LUMIX DC-G100D锛堝浐浠?VD3.80锛孲SID G100D-XXXXXX锛?
## 宸茬湡鏈洪獙璇?
- 鎵嬫満鍔犲叆鐩告満 Wi-Fi锛宍getstate` 杩斿洖 `ok`
- `accctrl` 璁块棶娴佺▼锛歚ok_under_research_no_msg` 鈫?`ok`锛堢浉鏈鸿嚜鍔ㄦ壒鍑嗭紝鏃犻渶寮圭獥锛?- UPnP `Browse /Server0/CDS_control` 杩斿洖 HTTP 200锛岃В鏋?66 涓獟浣撴潯鐩?- 鐩告満鏂囦欢鍒楄〃鏄剧ず鐪熷疄鍛藉悕锛?  - `P1002205.JPG`
  - `P1002252.RW2`
  - `P1002236.MP4`
- JPEG 浼犺緭鎴愬姛锛屼繚瀛樺埌 `Pictures/LumaLink`
- MP4 浼犺緭鎴愬姛锛屼繚瀛樺埌 `Movies/LumaLink`
- RW2 浼犺緭鎴愬姛锛屼繚瀛樺埌 `Download/LumaLink/RAW`
- RW2 鍐呭祵 JPEG 棰勮鎴愬姛锛孉pp 鐩稿唽鍙樉绀虹缉鐣ュ浘/璇︽儏
- 瀹炴椂鐩戠湅銆侀仴鎺ф媿鐓х瓑閬ユ帶鍔熻兘姝ゅ墠宸茬湡鏈洪獙璇?
## 鍏抽敭鎶€鏈偣

### 璁块棶涓庣洰褰?
```text
accctrl req_acc
-> ok_under_research_no_msg,...,remote,encrypted
-> ok,...,remote,encrypted
```

- 鐩告満杩斿洖 4 涓瓧娈碉紝`session=false`锛涗笉渚濊禆 `X-SESSION_ID`
- 姝ｇ‘鐨?ContentDirectory 鍦板潃锛歚/Server0/CDS_control`
- `/Lumix/Server0/CDS_control` 杩斿洖 404锛屽繀椤诲洖閫€
- 鐩告満 SOAP 鍝嶅簲浣跨敤骞冲彴 XML 瑙ｆ瀽鍣ㄥ彲鑳藉け璐ワ紱v1.0.6 璧蜂粠鍘熷 SOAP 姝ｅ垯鎻愬彇 `<Result>`锛屽啀瑙ｆ瀽 DIDL

### 涓€娆?Browse 涓€娆′笅杞?
G100D 鐨?50001 鏉冮檺琛ㄧ幇涓猴細

```text
Browse -> 涓€娆℃枃浠朵笅杞芥垚鍔?涓嬩竴娆′笅杞?-> Connection reset
閲嶆柊 accctrl + playmode + Browse -> 鍐嶄竴娆′笅杞芥垚鍔?```

v1.2.0 璧锋瘡涓枃浠朵笅杞藉墠鑷姩鍒锋柊锛?
- 鍏堝皾璇?UPnP Browse锛?- 403 鏃惰嚜鍔?`accctrl` + `playmode` + Browse锛?- 鍐嶈繘琛?50001 涓嬭浇銆?
### 鏂囦欢璧勬簮

DIDL 涓€涓?item 鍙寘鍚涓?`<res>`锛?
```text
CAM_ORG      -> JPEG 鍘熷浘
CAM_RAW_JPG  -> RAW 鐨?JPEG 棰勮
CAM_RAW      -> RW2 鍘熸枃浠?CAM_TN       -> 缂╃暐鍥撅紙璺宠繃锛?```

v1.2.1 璧疯В鏋愬叏閮?`<res>`锛屽垪琛ㄥ悓鏃舵樉绀?JPG 鍜?RW2銆?RW2 鏂囦欢澶翠负 Panasonic 鍙樹綋 `IIU\0`锛?9 49 55 00锛夛紝v1.2.2 璧锋牎楠屾帴鍙椼€?RW2 棰勮閫氳繃鎻愬彇鍐呭祵 JPEG 瀹炵幇锛屼笉鏄畬鏁?RAW 鏄惧奖銆?
### 涓嬭浇璇锋眰

```text
HttpURLConnection
User-Agent: Panasonic MIL DLNA CP UPnP/1.0
Accept-Encoding: identity
Accept: */*
```

涓嶄娇鐢?OkHttp 榛樿 gzip锛涗笅杞?URL 浣跨敤 DIDL 鐨?`DO...` 铏氭嫙璧勬簮鍚嶏紝涓嶄娇鐢?SD 鍗＄湡瀹?`P100...` 璺緞銆?
## 浜х墿

| 浜х墿 | 璺緞 |
| --- | --- |
| APK | `dist/LumaLink-1.3.1-debug.apk` |
| 婧愮爜 | `dist/LumaLink-1.3.1-src.zip` |
| 鏍￠獙 | `dist/SHA256SUMS.txt` |

```text
versionCode: 46
versionName: 1.3.1-debug
APK SHA-256: 721B065439287205028CED0A255A4CACD662947249B261D1A22F882D577CAC0E
婧愮爜 SHA-256: 8F8FD8F7A9196A4B0203C7AE8BEB55E3F93FA728373BA859607591D28F8C17C3
```

## 鏋勫缓涓庢祴璇?
```text
JAVA_HOME=D:\LumaLink\.dev\jdk
ANDROID_HOME=D:\LumaLink\.dev\android-sdk
GRADLE_USER_HOME=D:\DeepSeek Harness\projects\LumaLink\.gradle-home
ANDROID_USER_HOME=D:\DeepSeek Harness\projects\LumaLink\.android
```

- `:protocol:test` 38 涓祴璇曢€氳繃
- 鍏ㄩ噺 JVM 娴嬭瘯 51 涓€氳繃
- `:app:lintDebug` 閫氳繃锛宭int 0

## 鏄庣‘杈圭晫

- 涓嶅仛鍥轰欢/瀵勫瓨鍣?鍒嗗尯/绛惧悕鍐欏叆
- 涓嶈嚜鍔ㄥ啓鐩告満銆佷笉鍒犻櫎鐩告満鏂囦欢
- 鎺у埗鍛戒护浠嶄负鍥哄畾 allowlist锛岄渶瑕佺敤鎴风偣鍑?- 涓嶉€嗗悜鎴栧啋鍏呭畼鏂瑰姞瀵嗗崗璁?- RAW 浠呬娇鐢ㄥ唴宓?JPEG 棰勮锛屼笉鍋?RAW 鏄惧奖
- LUT 浠ｇ爜/鍗曟祴瀹屾垚锛涘畬鏁?RAW 鏄惧奖涓嶅紩鍏?libraw

## 宸茬煡闄愬埗

- 姣忎紶涓€涓枃浠堕渶瑕佸埛鏂颁竴娆＄浉鏈虹洰褰?璁块棶锛岄€熷害姣斿畼鏂?App 鎱?- 鐩告満 50001 鍋跺彂 `Connection reset`锛涘簲鐢ㄤ細鎸夋枃浠跺埛鏂板苟閲嶈瘯
- RAW 棰勮鏉ヨ嚜鍐呭祵 JPEG锛岃壊褰?瑁佸壀涓庣湡姝?RAW 鏄惧奖鍙兘涓嶅悓
- LUT 瑙嗛瀵煎嚭涓庤秴澶ф枃浠朵紶杈撲粛寤鸿鍗曠嫭鍋氶暱鏃堕棿鐪熸満鍥炲綊
