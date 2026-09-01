{
  description = "handball-recorder-android の開発環境（JDK / Gradle）";

  # nixpkgs だけに依存する。Rust も NDK も要らない — コアは handball-toolkit の
  # prebuilt .aar が .so ごと運んでくる（ADR 0006）。
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";

  outputs =
    { nixpkgs, ... }:
    let
      # fork した人がどの OS でも入れるように、対応システムを絞らない。
      systems = [
        "aarch64-darwin"
        "x86_64-darwin"
        "aarch64-linux"
        "x86_64-linux"
      ];
      forAllSystems = nixpkgs.lib.genAttrs systems;
    in
    {
      devShells = forAllSystems (
        system:
        let
          pkgs = nixpkgs.legacyPackages.${system};
        in
        {
          # mkShellNoCC: Android ビルドに C コンパイラは要らない（.so は .aar 同梱）。
          default = pkgs.mkShellNoCC {
            packages = [
              # JDK はこの flake が固定する。**Gradle のバージョンを決めるのは
              # gradle/wrapper/gradle-wrapper.properties（wrapper）であって、この flake ではない** —
              # nix を持たない fork 先と手元で同じ Gradle を使わせるため。
              pkgs.jdk21

              # nix 環境で wrapper のダウンロードを踏みたくないとき用に gradle も置く。
              # 下の shellHook が wrapper のピン留めとバージョンが一致しているかを検査する。
              pkgs.gradle
            ];

            # ./gradlew はこの JAVA_HOME で動く。
            JAVA_HOME = pkgs.jdk21.home;

            # Android SDK はこの flake では持たない（ADR 0006 決定 1。SDK は 10 GiB 超で
            # closure に載せる規模ではない）。ホストの ANDROID_HOME か local.properties から取る。
            shellHook = ''
              if [ -z "''${ANDROID_HOME:-}" ] && [ ! -f local.properties ]; then
                echo "warn: ANDROID_HOME が未設定で local.properties もありません。" >&2
                echo "      Android SDK の場所を local.properties に書いてください:" >&2
                echo "      echo \"sdk.dir=\$HOME/Library/Android/sdk\" > local.properties" >&2
              fi

              # Gradle のバージョンを二重管理にしないための検査。nixpkgs が gradle を上げると
              # wrapper のピン留めと食い違い、AGP の要求（README「バージョンの対応関係」）と
              # ずれる。散文の注意書きでは守れないのでシェルに入るたび機械が見る。
              _hbra_wrapper_props=gradle/wrapper/gradle-wrapper.properties
              if [ -f "$_hbra_wrapper_props" ]; then
                _hbra_wrapper_ver=$(sed -n 's/.*gradle-\([0-9][0-9.]*\)-bin\.zip.*/\1/p' "$_hbra_wrapper_props")
                if [ -n "$_hbra_wrapper_ver" ] && [ "$_hbra_wrapper_ver" != "${pkgs.gradle.version}" ]; then
                  echo "warn: gradle のバージョンが食い違っています — wrapper: $_hbra_wrapper_ver / nix: ${pkgs.gradle.version}" >&2
                  echo "      ./gradlew を使う（wrapper が正）か、どちらかを合わせてください。" >&2
                  echo "      README「バージョンの対応関係」の AGP 要求も同時に確認すること。" >&2
                fi
                unset _hbra_wrapper_ver
              fi
              unset _hbra_wrapper_props
            '';
          };
        }
      );
    };
}
