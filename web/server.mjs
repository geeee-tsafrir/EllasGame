import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";
import { createServer } from "node:http";
import { extname, join, normalize, resolve } from "node:path";

const port = Number.parseInt(process.env.PORT ?? "5173", 10);
const host = process.argv.includes("--host") ? "0.0.0.0" : "127.0.0.1";
const publicDir = resolve("public");

const contentTypes = new Map([
  [".css", "text/css; charset=utf-8"],
  [".html", "text/html; charset=utf-8"],
  [".ico", "image/x-icon"],
  [".js", "text/javascript; charset=utf-8"],
  [".json", "application/json; charset=utf-8"],
  [".png", "image/png"],
  [".svg", "image/svg+xml; charset=utf-8"],
  [".webmanifest", "application/manifest+json; charset=utf-8"]
]);

createServer(async (request, response) => {
  const url = new URL(request.url ?? "/", `http://${request.headers.host}`);
  const requestedPath = normalize(decodeURIComponent(url.pathname));
  const relativePath = requestedPath === "/" ? "index.html" : requestedPath.replace(/^\/+/, "");
  const filePath = resolve(join(publicDir, relativePath));

  if (!filePath.startsWith(publicDir)) {
    response.writeHead(403);
    response.end("Forbidden");
    return;
  }

  const servedPath = await existingFilePath(filePath);
  if (servedPath === null) {
    response.writeHead(404);
    response.end("Not found");
    return;
  }

  response.writeHead(200, {
    "Content-Type": contentTypes.get(extname(servedPath)) ?? "application/octet-stream"
  });
  createReadStream(servedPath).pipe(response);
}).listen(port, host, () => {
  console.log(`EllasGame web running at http://${host}:${port}`);
});

async function existingFilePath(filePath) {
  try {
    const fileStat = await stat(filePath);
    if (fileStat.isFile()) {
      return filePath;
    }
    if (fileStat.isDirectory()) {
      const indexPath = join(filePath, "index.html");
      const indexStat = await stat(indexPath);
      return indexStat.isFile() ? indexPath : null;
    }
  } catch {
    return null;
  }
  return null;
}
