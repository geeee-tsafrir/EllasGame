import { copyFile, mkdir } from "node:fs/promises";
import { resolve } from "node:path";

const sharedIcons = [
  "camera-steampunk.png",
  "keyboard-steampunk.png",
  "sketchpad-steampunk.png"
];
const desktopIcons = [
  "play.png",
  "settings.png"
];

const sharedSourceDir = resolve("..", "assets", "icons");
const desktopSourceDir = resolve("..", "desktop", "src", "main", "resources", "icons");
const outputDir = resolve("public", "icons");

await mkdir(outputDir, { recursive: true });
for (const iconName of sharedIcons) {
  await copyFile(resolve(sharedSourceDir, iconName), resolve(outputDir, iconName));
}
for (const iconName of desktopIcons) {
  await copyFile(resolve(desktopSourceDir, iconName), resolve(outputDir, iconName));
}

console.log(`Copied ${sharedIcons.length + desktopIcons.length} web icons to ${outputDir}`);
