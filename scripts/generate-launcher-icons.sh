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
SCALE=0.8
TX=102.4
TY=130.8
# 旧形式アイコンは「マスクが見せる内側 72dp」を 1024px いっぱいに引き伸ばしたもの。
#   1024 換算の安全域 = 1024 * 18/108 .. 1024 * 90/108 = 170.667 .. 853.333
#   拡大率 = 108/72 = 1.5
# 合成すると  x' = 1.5*(SCALE*x + TX) - 256,  y' = 1.5*(SCALE*y + TY) - 256
LEG_SCALE=1.2             # = 1.5 * 0.8
LEG_TX=-102.4            # = 1.5 * 102.4 - 256
LEG_TY=-59.8             # = 1.5 * 130.8 - 256
# 背景グラデーションは 108dp キャンバス全体に掛かっているので、切り出した後の 1024px 上では
# 開始 / 終了が外側にずれる（1.5*(0-170.667) = -256 / 1.5*(1024-170.667) = 1280）。
GRAD_Y1=-256
GRAD_Y2=1280
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
check 'android:startX="54"'           "$BG" "背景グラデーションが縦方向の全面グラデーションではありません。"
check 'android:startY="0"'            "$BG" "背景グラデーションが縦方向の全面グラデーションではありません。"
check 'android:endY="108"'            "$BG" "背景グラデーションが縦方向の全面グラデーションではありません。"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

# ── ベクター XML から色とパスを取り出す ────────────────────────────────────────
awk '
    /<item / {
        off = $0; sub(/.*android:offset="/, "", off); sub(/".*/, "", off)
        col = $0; sub(/.*android:color="/,  "", col); sub(/".*/, "", col)
        a = toupper(substr(col, 2, 2))
        if (a != "FF") { print "背景グラデーションの半透明には未対応です: " col > "/dev/stderr"; exit 1 }
        printf "      <stop offset=\"%s\" stop-color=\"#%s\"/>\n", off, substr(col, 4, 6)
    }
' "$BG" > "$TMP_DIR/stops.svg"
[ -s "$TMP_DIR/stops.svg" ] || { echo "背景グラデーションの stop を読めませんでした。" >&2; exit 1; }

awk '
    /android:fillColor="/ { c = $0; sub(/.*android:fillColor="/, "", c); sub(/".*/, "", c); a = "1" }
    /android:fillAlpha="/ { a = $0; sub(/.*android:fillAlpha="/, "", a); sub(/".*/, "", a) }
    /android:pathData="/  {
        d = $0; sub(/.*android:pathData="/, "", d); sub(/".*/, "", d)
        printf "    <path d=\"%s\" fill=\"%s\" fill-opacity=\"%s\"/>\n", d, c, a
        n++
    }
    END { if (n != 5) { print "前景のパスが 5 本ではありません: " n > "/dev/stderr"; exit 1 } }
' "$FG" > "$TMP_DIR/paths.svg"

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
    <linearGradient id="bg" gradientUnits="userSpaceOnUse" x1="512" y1="$GRAD_Y1" x2="512" y2="$GRAD_Y2">
$(cat "$TMP_DIR/stops.svg")
    </linearGradient>
  </defs>
  <g clip-path="url(#m)">
    <rect x="0" y="0" width="1024" height="1024" fill="url(#bg)"/>
    <g transform="translate($LEG_TX,$LEG_TY) scale($LEG_SCALE)">
$(cat "$TMP_DIR/paths.svg")
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
