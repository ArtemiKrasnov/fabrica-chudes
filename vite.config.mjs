import { defineConfig } from "vite";

export default defineConfig({
  base: process.env.GITHUB_ACTIONS === "true" ? "/fabrica-chudes/" : "/",
  server: {
    proxy: {
      "/api": "http://localhost:3001",
    },
  },
});
