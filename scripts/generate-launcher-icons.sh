#!/usr/bin/env bash
# ランチャーアイコンの PNG フォールバック（mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher*.png）
# を生成する。
#
# 使い方:
#   scripts/generate-launcher-icons.sh
#
# なぜ要るか:
#   minSdk = 24 なので **API 24/25 では adaptive icon（mipmap-anydpi-v26/）が使われない**。
#   この PNG が無いとその世代の端末でアイコンが出ない。
#
# 出どころは 1 つ:
#   形・色は res/drawable/ic_launcher_foreground.xml と ic_launcher_background.xml から
#   **読み出す**。このスクリプトは色もパスも持たない。意匠を直すときはベクター側の XML を
#   直してここを回す（PNG を直接編集しない）。テーマアイコン層
#   ic_launcher_monochrome.xml は前景と同じ形を単色にしたもので、こちらも手で同期させる。
#
# 前提: macOS（sips 使用）+ Google Chrome。CI では回さない（生成物はコミット済み）。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RES="$REPO_ROOT/app/src/main/res"
FG="$RES/drawable/ic_launcher_foreground.xml"
BG="$RES/drawable/ic_launcher_background.xml"
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"

# adaptive icon の <foreground> に掛けている group 変換。ベクター側と食い違うと PNG だけ
# 別の絵になるので、読み出した値と突き合わせて食い違ったら止める。
SCALE=0.86
TX=71.68
TY=71.68
# 前景のパス本数（影 2 段 × 7 + 面 7 + 縁の光 7）。増減に気付かず PNG だけ古いままに
# なるのを防ぐ。
FG_PATHS=28
# 旧形式アイコンは「マスクが見せる内側 72dp」を 1024px いっぱいに引き伸ばしたもの。
#   crop = 108dp のうち内側 72dp → 拡大率 108/72 = 1.5、原点は 1024*18/108 = 170.667
#   合成すると  transform="translate(-256,-256) scale(1.5)"（前景の 1024 座標系に対して）
LEG_SCALE=1.5
LEG_OFF=-256
# 背景は viewport が 108 なので、1024 へ載せる 1024/108 = 9.481481 も掛ける
# （9.481481 * 1.5 = 14.222222）。
LEG_BG_SCALE=14.222222
# グラデーションは gradientUnits="userSpaceOnUse" でパスと同じ座標系に置くので、
# 上の transform がそのまま効く（座標の付け替えは要らない）。
#
# 旧形式アイコンの角丸半径（1024px 基準）。adaptive icon と違って端末側のマスクが無いので、
# ここで付けないと ic_launcher.png が真四角になる。
CORNER=225

for f in "$FG" "$BG"; do
    [ -f "$f" ] || { echo "見つかりません: $f" >&2; exit 1; }
done
command -v sips >/dev/null || { echo "sips が要ります（macOS 前提）。" >&2; exit 1; }
[ -x "$CHROME" ] || { echo "Chrome が見つかりません: $CHROME（CHROME= で指定できます）" >&2; exit 1; }

# ── ベクター側の前提を検査する（黙ってずれるのを防ぐ） ──────────────────────────
check() {
    grep -Fq "$1" "$2" || { echo "$3" >&2; echo "  期待: $1" >&2; echo "  対象: $2" >&2; exit 1; }
}
check 'android:viewportWidth="1024"' "$FG" "前景の viewport が 1024 ではありません。"
check "android:scaleX=\"$SCALE\""     "$FG" "前景の group 変換がスクリプトの定数と違います。"
check "android:translateX=\"$TX\""    "$FG" "前景の group 変換がスクリプトの定数と違います。"
check "android:translateY=\"$TY\""    "$FG" "前景の group 変換がスクリプトの定数と違います。"
check 'android:viewportWidth="108"'   "$BG" "背景の viewport が 108 ではありません。"
check 'android:startX="54"'           "$BG" "背景グラデーションが縦方向の全面グラデーションではありません。"
check 'android:startY="0"'            "$BG" "背景グラデーションが縦方向の全面グラデーションではありません。"
check 'android:endY="108"'            "$BG" "背景グラデーションが縦方向の全面グラデーションではありません。"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

# ── ベクター XML を SVG の断片へ変換する ───────────────────────────────────────
# <path> を上から順に読み、面（単色 / linear gradient）と線（stroke）をそのまま写す。
# 出力は defs（gradient 定義）と body（path 本体）の 2 ファイル。
vector_to_svg() {  # $1 = 入力 XML, $2 = id 接頭辞, $3 = defs 出力, $4 = body 出力
    awk -v IDP="$2" -v DEFS="$3" -v BODY="$4" '
        function hexv(s,   i, c, v, d) {
            v = 0
            for (i = 1; i <= length(s); i++) {
                c = toupper(substr(s, i, 1))
                d = index("0123456789ABCDEF", c) - 1
                if (d < 0) { print "16 進として読めません: " s > "/dev/stderr"; exit 1 }
                v = v * 16 + d
            }
            return v
        }
        # #AARRGGBB / #RRGGBB を CSS の色 + 不透明度へ。結果は COL / OPA に入れる。
        function css(c) {
            sub(/^#/, "", c)
            if (length(c) == 8)      { OPA = hexv(substr(c, 1, 2)) / 255; COL = "#" substr(c, 3, 6) }
            else if (length(c) == 6) { OPA = 1;                           COL = "#" c }
            else { print "色の形式が想定外です: " c > "/dev/stderr"; exit 1 }
        }
        function attr(s, name,   t) {
            t = s
            if (index(t, name "=\"") == 0) return ""
            sub(".*" name "=\"", "", t)
            sub("\".*", "", t)
            return t
        }
        function emit(   fillattr, fillop, out, gid) {
            np++
            fillattr = "none"; fillop = 1
            if (hasgrad) {
                ngrad++
                gid = IDP "g" ngrad
                printf("    <linearGradient id=\"%s\" gradientUnits=\"userSpaceOnUse\" x1=\"%s\" y1=\"%s\" x2=\"%s\" y2=\"%s\">\n%s    </linearGradient>\n",
                       gid, gx1, gy1, gx2, gy2, stops) > DEFS
                fillattr = "url(#" gid ")"
            } else if (fc != "") {
                css(fc); fillattr = COL; fillop = OPA
                if (fa != "") fillop = fillop * fa
            }
            out = sprintf("    <path d=\"%s\" fill=\"%s\" fill-opacity=\"%.4f\"", d, fillattr, fillop)
            if (sc != "") {
                css(sc)
                out = out sprintf(" stroke=\"%s\" stroke-opacity=\"%.4f\" stroke-width=\"%s\"", COL, OPA, sw)
            }
            print out "/>" > BODY
            inpath = 0
        }
        /<path/ && !/<\/path>/ { inpath = 1; d = ""; fc = ""; fa = ""; sc = ""; sw = ""; hasgrad = 0; stops = "" }
        inpath && /android:pathData="/    { d  = attr($0, "android:pathData") }
        inpath && /android:fillColor="#/  { fc = attr($0, "android:fillColor") }
        inpath && /android:fillAlpha="/   { fa = attr($0, "android:fillAlpha") }
        inpath && /android:strokeColor="/ { sc = attr($0, "android:strokeColor") }
        inpath && /android:strokeWidth="/ { sw = attr($0, "android:strokeWidth") }
        inpath && /<gradient/ { hasgrad = 1 }
        inpath && /android:type="/ {
            if (attr($0, "android:type") != "linear") {
                print "linear 以外のグラデーションには未対応です: " attr($0, "android:type") > "/dev/stderr"; exit 1
            }
        }
        inpath && /android:startX="/ { gx1 = attr($0, "android:startX") }
        inpath && /android:startY="/ { gy1 = attr($0, "android:startY") }
        inpath && /android:endX="/   { gx2 = attr($0, "android:endX") }
        inpath && /android:endY="/   { gy2 = attr($0, "android:endY") }
        inpath && /<item / {
            css(attr($0, "android:color"))
            stops = stops sprintf("      <stop offset=\"%s\" stop-color=\"%s\" stop-opacity=\"%.4f\"/>\n",
                                  attr($0, "android:offset"), COL, OPA)
        }
        inpath && /<\/path>/ { emit() }
        inpath && /\/>[ \t]*$/ && !/<item / && !/<gradient/ { emit() }
        END { print np }
    ' "$1"
}

FG_N="$(vector_to_svg "$FG" fg "$TMP_DIR/fg_defs.svg" "$TMP_DIR/fg_body.svg")"
BG_N="$(vector_to_svg "$BG" bg "$TMP_DIR/bg_defs.svg" "$TMP_DIR/bg_body.svg")"
[ "$FG_N" = "$FG_PATHS" ] || {
    echo "前景のパスが $FG_PATHS 本ではありません: $FG_N" >&2
    echo "  意図して増減させたなら scripts/generate-launcher-icons.sh の FG_PATHS も直すこと。" >&2
    exit 1
}
[ "$BG_N" = 1 ] || { echo "背景のパスが 1 本ではありません: $BG_N" >&2; exit 1; }
[ -s "$TMP_DIR/bg_defs.svg" ] || { echo "背景グラデーションを読めませんでした。" >&2; exit 1; }

# ── 1024px のマスタ SVG を組む（shape = round / rounded） ──────────────────────
master_svg() {  # $1 = round|rounded
    if [ "$1" = round ]; then
        clip='<circle cx="512" cy="512" r="512"/>'
    else
        clip="<rect x=\"0\" y=\"0\" width=\"1024\" height=\"1024\" rx=\"$CORNER\" ry=\"$CORNER\"/>"
    fi
    cat <<SVG
<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
  <defs>
    <clipPath id="m">$clip</clipPath>
$(cat "$TMP_DIR/bg_defs.svg")
$(cat "$TMP_DIR/fg_defs.svg")
  </defs>
  <g clip-path="url(#m)">
    <g transform="translate($LEG_OFF,$LEG_OFF) scale($LEG_BG_SCALE)">
$(cat "$TMP_DIR/bg_body.svg")
    </g>
    <g transform="translate($LEG_OFF,$LEG_OFF) scale($LEG_SCALE)">
      <g transform="translate($TX,$TY) scale($SCALE)">
$(cat "$TMP_DIR/fg_body.svg")
      </g>
    </g>
  </g>
</svg>
SVG
}

render() {  # $1 = round|rounded, $2 = 出力 PNG
    master_svg "$1" > "$TMP_DIR/i.svg"
    cat > "$TMP_DIR/i.html" <<HTML
<!DOCTYPE html><html><head><meta charset="UTF-8"><style>
html,body{margin:0;padding:0;width:1024px;height:1024px;background:transparent}
img{display:block;width:1024px;height:1024px}
</style></head><body><img src="i.svg"></body></html>
HTML
    "$CHROME" --headless=new --disable-gpu --hide-scrollbars \
        --default-background-color=00000000 --window-size=1024,1024 \
        --screenshot="$2" "file://$TMP_DIR/i.html" >/dev/null 2>&1
    [ -s "$2" ] || { echo "Chrome が PNG を書けませんでした: $2" >&2; exit 1; }
}

render rounded "$TMP_DIR/ic_launcher.png"
render round   "$TMP_DIR/ic_launcher_round.png"

# ── 各 dpi へ縮小して配置する ─────────────────────────────────────────────────
# 48dp のアイコンを密度ごとの px にしたもの（mdpi = 1x が 48px）。
for entry in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
    dpi=${entry%%:*}; px=${entry##*:}
    mkdir -p "$RES/mipmap-$dpi"
    for name in ic_launcher ic_launcher_round; do
        sips -Z "$px" "$TMP_DIR/$name.png" --out "$RES/mipmap-$dpi/$name.png" >/dev/null
    done
done

echo "生成しました:"
for entry in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
    dpi=${entry%%:*}
    for name in ic_launcher ic_launcher_round; do
        f="$RES/mipmap-$dpi/$name.png"
        printf '  %-42s ' "mipmap-$dpi/$name.png"
        sips -g pixelWidth -g pixelHeight "$f" | tail -2 | tr -d '\n' | tr -s ' '
        echo
    done
done
