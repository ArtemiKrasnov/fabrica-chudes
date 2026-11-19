import { spawn } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const projectDirectory = path.resolve(scriptDirectory, "..");
const wrapperJar = path.join(projectDirectory, ".mvn", "wrapper", "maven-wrapper.jar");
const wrapperArguments = [
  "-pl",
  "server-java",
  ...(process.argv.slice(2).length ? process.argv.slice(2) : ["spring-boot:run"]),
];

const backend = spawn(
  "java",
  [
    "-classpath",
    wrapperJar,
    `-Dmaven.multiModuleProjectDirectory=${projectDirectory}`,
    "org.apache.maven.wrapper.MavenWrapperMain",
    ...wrapperArguments,
  ],
  { cwd: projectDirectory, stdio: "inherit" },
);

backend.on("error", (error) => {
  console.error("Не удалось запустить Spring Boot backend:", error.message);
  process.exitCode = 1;
});
backend.on("exit", (code) => {
  process.exitCode = code ?? 1;
});

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => backend.kill(signal));
}
