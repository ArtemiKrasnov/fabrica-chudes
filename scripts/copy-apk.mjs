import { copyFile, mkdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const projectDirectory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const source = path.join(projectDirectory, "android", "app", "build", "outputs", "apk", "debug", "app-debug.apk");
const outputDirectory = path.join(projectDirectory, "apk");
const destination = path.join(outputDirectory, "fabrica-chudes-debug.apk");

await mkdir(outputDirectory, { recursive: true });
await copyFile(source, destination);
console.log(`APK copied to ${destination}`);
