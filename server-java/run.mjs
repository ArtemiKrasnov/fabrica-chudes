import { spawn, spawnSync } from "node:child_process";
import { mkdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const projectDirectory = path.resolve(scriptDirectory, "..");
const classesDirectory = path.join(scriptDirectory, "build", "classes");
const sources = [
  path.join(scriptDirectory, "src", "main", "java", "ru", "plusrep", "fabrica", "Json.java"),
  path.join(scriptDirectory, "src", "main", "java", "ru", "plusrep", "fabrica", "FactoryWonderServer.java"),
];

mkdirSync(classesDirectory, { recursive: true });

const compilation = spawnSync(
  "javac",
  ["--add-modules", "jdk.httpserver", "-encoding", "UTF-8", "-d", classesDirectory, ...sources],
  { cwd: projectDirectory, stdio: "inherit" },
);

if (compilation.error) {
  console.error("Не удалось запустить javac:", compilation.error.message);
  process.exit(1);
}
if (compilation.status !== 0) process.exit(compilation.status ?? 1);

const backend = spawn(
  "java",
  ["--add-modules", "jdk.httpserver", "-cp", classesDirectory, "ru.plusrep.fabrica.FactoryWonderServer"],
  { cwd: projectDirectory, stdio: "inherit" },
);

backend.on("error", (error) => {
  console.error("Не удалось запустить Java backend:", error.message);
  process.exitCode = 1;
});
backend.on("exit", (code) => {
  process.exitCode = code ?? 1;
});

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => backend.kill(signal));
}
