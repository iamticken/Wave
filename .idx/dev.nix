
{ pkgs, ... }: {
  channel = "stable-23.11";
  packages = [
    pkgs.jdk
  ];
  idx.previews = {
    enable = true;
  };
}
