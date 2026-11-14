{
  description = "SMS2Email development shell";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";

  outputs = { nixpkgs, ... }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs { inherit system; };
      emulatorLibraries = with pkgs; [
        alsa-lib
        dbus
        expat
        fontconfig
        freetype
        glib
        libbsd
        libdrm
        libice
        libglvnd
        libpng
        libsm
        libuuid
        libx11
        libxcomposite
        libxdamage
        libxext
        libxfixes
        libxi
        libxkbcommon
        libxkbfile
        libxrandr
        libxrender
        libxshmfence
        libxcb
        xcb-util-cursor
        xcbutilimage
        xcbutilkeysyms
        xcbutilrenderutil
        xcbutilwm
        mesa
        nspr
        nss
        pulseaudio
        wayland
        zlib
      ];
    in {
      devShells.${system}.default = pkgs.mkShell {
        packages = [ pkgs.just ];

        shellHook = ''
          export ANDROID_SDK_ROOT="''${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
          export QT_QPA_PLATFORM="xcb"
          export LD_LIBRARY_PATH="$ANDROID_SDK_ROOT/emulator/lib64:$ANDROID_SDK_ROOT/emulator/lib64/qt/lib:${pkgs.lib.makeLibraryPath emulatorLibraries}:''${LD_LIBRARY_PATH:-}"
        '';
      };
    };
}
